package com.mesosphere.cosmos

import java.util.UUID

import cats.data.Xor.Right
import cats.std.list._
import cats.syntax.traverse._
import com.twitter.finagle.http._
import com.twitter.finagle.{Http, Service}
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, TimeoutException}
import io.circe.generic.auto._
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Cursor, Json}
import org.scalatest.FreeSpec

final class PackageInstallSpec extends FreeSpec with CosmosSpec {

  import PackageInstallSpec._

  "The package install endpoint" - {

    "can successfully deploy a service to Marathon" in {
      runService() { apiClient =>
        forAll (PackageTable) { (packageName, packageJson) =>
          apiClient.installPackageAndAssert(
            packageName,
            Status.Ok,
            contentString = "",
            preInstallState = NotInstalled,
            postInstallState = Installed
          )

          assertPackageInstalledFromCache(packageName, packageJson)

          // TODO: Uninstall the package, or use custom Marathon app IDs to avoid name clashes
        }
      }
    }

    "reports an error if the requested package is not in the cache" in {
      forAll (PackageTable) { (packageName, packageJson) =>
        val packageCache = MemoryPackageCache(PackageMap - packageName)

        runService(packageCache = packageCache) { apiClient =>
          apiClient.installPackageAndAssert(
            packageName,
            Status.NotFound,
            contentString = s"Package [$packageName] not found",
            preInstallState = Anything,
            postInstallState = Unchanged
          )
        }
      }
    }

    "reports an error if the request to Marathon fails" - {

      "due to the package already being installed" in {
        runService() { apiClient =>
          forAll (PackageTable) { (packageName, packageJson) =>
            // TODO This currently relies on test execution order to be correct
            // Update it to explicitly install a package twice
            apiClient.installPackageAndAssert(
              packageName,
              Status.Conflict,
              contentString = s"Package is already installed",
              preInstallState = AlreadyInstalled,
              postInstallState = Unchanged
            )
          }
        }
      }

      "with a generic client error" in {
        // Taken from Marathon API docs
        val clientErrorStatuses = Table("status", Seq(400, 401, 403, 422).map(Status.fromCode): _*)

        forAll (clientErrorStatuses) { status =>
          val dcosClient = Service.const(Future.value(Response(status)))

          runService(dcosClient = dcosClient) { apiClient =>
            forAll(PackageTable) { (packageName, packageJson) =>
              apiClient.installPackageAndAssert(
                packageName,
                Status.InternalServerError,
                contentString = s"Received response status code ${status.code} from Marathon",
                preInstallState = Anything,
                postInstallState = Unchanged
              )
            }
          }
        }
      }

      "with a generic server error" in {
        val serverErrorStatuses = Table("status", Seq(500, 502, 503, 504).map(Status.fromCode): _*)

        forAll (serverErrorStatuses) { status =>
          val dcosClient = Service.const(Future.value(Response(status)))

          runService(dcosClient = dcosClient) { apiClient =>
            forAll (PackageTable) { (packageName, packageJson) =>
              apiClient.installPackageAndAssert(
                packageName,
                Status.BadGateway,
                contentString = s"Received response status code ${status.code} from Marathon",
                preInstallState = Anything,
                postInstallState = Unchanged
              )
            }
          }
        }
      }

      "by timing out" in {
        val dcosClient = Service.const(Future.exception(new TimeoutException("Request timed out")))

        runService(dcosClient = dcosClient) { apiClient =>
          forAll (PackageTable) { (packageName, packageJson) =>
            apiClient.installPackageAndAssert(
              packageName,
              Status.BadGateway,
              contentString = "Marathon request timed out",
              preInstallState = Anything,
              postInstallState = Unchanged
            )
          }
        }
      }

      "with an unknown error" in {
        val errorMessage = "BOOM!"
        val dcosClient = Service.const(Future.exception(new Throwable(errorMessage)))

        runService(dcosClient = dcosClient) { apiClient =>
          forAll (PackageTable) { (packageName, packageJson) =>
            apiClient.installPackageAndAssert(
              packageName,
              Status.BadGateway,
              contentString = s"Unknown Marathon request error: $errorMessage",
              preInstallState = Anything,
              postInstallState = Unchanged
            )
          }
        }
      }

    }

  }

  private[this] def runService[A](
    dcosClient: Service[Request, Response] = Http.newService(s"${Config.DcosHost}:80"),
    packageCache: PackageCache = MemoryPackageCache(PackageMap)
  )(
    f: ApiTestAssertionDecorator => Unit
  ): Unit = {
    val service = new Cosmos(packageCache, new MarathonPackageRunner(dcosClient)).service
    val server = Http.serve(s":$servicePort", service)
    val client = Http.newService(s"127.0.0.1:$servicePort")

    try {
      f(new ApiTestAssertionDecorator(client))
    } finally {
      Await.all(server.close(), client.close(), service.close())
    }
  }

}

private object PackageInstallSpec extends CosmosSpec {

  private val PackageTableRows: Seq[(String, Json)] = Seq(
    packageTableRow("helloworld2", 1, 512, 2),
    packageTableRow("helloworld3", 0.75, 256, 3)
  )

  private lazy val PackageTable = Table(
    ("package name", "Marathon JSON"),
    PackageTableRows: _*
  )

  private lazy val PackageMap: Map[String, String] = PackageTableRows.toMap.mapValues(_.noSpaces)

  private def packageTableRow(
    name: String, cpus: Double, mem: Int, pythonVersion: Int
  ): (String, Json) = {
    val cmd =
      if (pythonVersion <= 2) "python2 -m SimpleHTTPServer 8082" else "python3 -m http.server 8083"

    name -> Json.obj(
      "id" -> name,
      "cpus" -> cpus,
      "mem" -> mem,
      "instances" -> 1,
      "cmd" -> cmd,
      "container" -> Json.obj(
        "type" -> "DOCKER",
        "docker" -> Json.obj(
          "image" -> s"python:$pythonVersion",
          "network" -> "HOST"
        )
      ),
      "labels" -> Json.obj(
        "test-id" -> UUID.randomUUID().toString
      )
    )
  }

  private def assertPackageInstalledFromCache(packageName: String, packageJson: Json): Unit = {
    val expectedLabel = extractTestLabel(packageJson.cursor)
    val actualLabel = getMarathonJsonTestLabel(packageName)
    typedAssertResult(expectedLabel)(actualLabel)
  }

  private def getMarathonJsonTestLabel(packageName: String): Option[String] = {
    val marathonClient = Http.newService(s"${Config.DcosHost}:80")
    val request = RequestBuilder()
      .url(s"http://${Config.DcosHost}/marathon/v2/apps/$packageName")
      .buildGet()
    val response = Await.result(marathonClient(request))
    val Right(parsed) = parse(response.contentString)
    parsed.cursor.downField("app").flatMap(extractTestLabel)
  }

  private def extractTestLabel(marathonJsonCursor: Cursor): Option[String] = {
    marathonJsonCursor
      .downField("labels")
      .flatMap(_.downField("test-id"))
      .flatMap(_.as[String].toOption)
  }

}

private final class ApiTestAssertionDecorator(apiClient: Service[Request, Response])
  extends CosmosSpec {

  import ApiTestAssertionDecorator._

  private[cosmos] def installPackageAndAssert(
    packageName: String,
    status: Status,
    contentString: String,
    preInstallState: PreInstallState,
    postInstallState: PostInstallState
  ): Unit = {
    val packageWasInstalled = isPackageInstalled(packageName)
    preInstallState match {
      case AlreadyInstalled => typedAssertResult(true)(packageWasInstalled)
      case NotInstalled => typedAssertResult(false)(packageWasInstalled)
      case Anything => // Don't care
    }

    val response = installPackage(apiClient, packageName)
    typedAssertResult(status)(response.status)
    typedAssertResult(contentString)(response.contentString)

    val expectedInstalled = postInstallState match {
      case Installed => true
      case Unchanged => packageWasInstalled
    }
    val actuallyInstalled = isPackageInstalled(packageName)
    typedAssertResult(expectedInstalled)(actuallyInstalled)
  }

  private[this] def isPackageInstalled(packageName: String): Boolean = {
    listMarathonAppIds().contains(s"/$packageName")
  }

  private[this] def listMarathonAppIds(): Seq[String] = {
    val marathonClient = Http.newService(s"${Config.DcosHost}:80")
    val request = RequestBuilder()
      .url(s"http://${Config.DcosHost}/marathon/v2/apps")
      .buildGet()
    val response = Await.result(marathonClient(request))
    val Right(jsonContent) = parse(response.contentString)
    val Right(appCursors) = jsonContent.cursor.get[List[Json]]("apps")
    val Right(appIds) = appCursors.traverseU(_.cursor.get[String]("id"))
    appIds
  }

  private[this] def installPackage(
    apiClient: Service[Request, Response], packageName: String
  ): Response = {
    val installRequest = requestBuilder(InstallEndpoint)
      .buildPost(Buf.Utf8(InstallRequest(packageName).asJson.noSpaces))
    Await.result(apiClient(installRequest))
  }

}

private object ApiTestAssertionDecorator {

  private val InstallEndpoint: String = "v1/package/install"

}

private sealed trait PreInstallState
private case object AlreadyInstalled extends PreInstallState
private case object NotInstalled extends PreInstallState
private case object Anything extends PreInstallState

private sealed trait PostInstallState
private case object Installed extends PostInstallState
private case object Unchanged extends PostInstallState
