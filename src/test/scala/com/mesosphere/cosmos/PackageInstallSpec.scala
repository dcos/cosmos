package com.mesosphere.cosmos

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, SimpleFileVisitor, Path, Files}
import java.util.UUID

import cats.data.Xor.Right
import cats.std.list._
import cats.syntax.traverse._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.http._
import com.twitter.finagle.{Http, Service}
import com.twitter.io.Buf
import com.twitter.util._
import io.circe.generic.auto._
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Cursor, Json}
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

final class PackageInstallSpec extends FreeSpec with CosmosSpec with BeforeAndAfterAll {

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

    "can successfully install packages from Universe" in {
      val _ = withTempDirectory { universeDir =>
        val universeCache = Await.result(UniversePackageCache(UniverseUri, universeDir))

        runService(packageCache = universeCache) { apiClient =>
          forAll (UniversePackagesTable) { (packageName, appId, uriSet) =>
            apiClient.installPackageAndAssert(
              packageName,
              Status.Ok,
              contentString = "",
              preInstallState = NotInstalled,
              postInstallState = Installed,
              appIdOpt = Some(appId)
            )
            // TODO Confirm that the correct config was sent to Marathon - see issue #38
            val uris = Await.result(getPackageUris(appId))
            typedAssertResult(uriSet)(uris)
          }
        }
      }
    }

  }

  override protected def beforeAll(): Unit = { /*no-op*/ }

  override protected def afterAll(): Unit = {
    // TODO: This should actually happen between each test, but for now tests depend on eachother :(
    val deletes: Future[Seq[Unit]] = Future.collect(Seq(
      adminRouter.deleteApp("/helloworld", force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp("/helloworld2", force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp("/helloworld3", force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp("/cassandra/dcos", force = true) map { resp => assert(resp.getStatusCode() === 200) }
    ))
    Await.result(deletes.flatMap { x => Future.Unit })
  }

  private[this] def runService[A](
    dcosClient: Service[Request, Response] = Services.adminRouterClient(dcosHost()),
    packageCache: PackageCache = MemoryPackageCache(PackageMap)
  )(
    f: ApiTestAssertionDecorator => Unit
  ): Unit = {
    val adminRouter = new AdminRouter(dcosHost(), dcosClient)
    val service = new Cosmos(packageCache, new MarathonPackageRunner(adminRouter)).service
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

  private val UniverseUri = Uri.parse("https://github.com/mesosphere/universe/archive/cli-test-3.zip")

  private val PackageTableRows: Seq[(String, Json)] = Seq(
    packageTableRow("helloworld2", 1, 512, 2),
    packageTableRow("helloworld3", 0.75, 256, 3)
  )

  private lazy val PackageTable = Table(
    ("package name", "Marathon JSON"),
    PackageTableRows: _*
  )

  private val UniversePackagesTable = Table(
    ("package name", "app id", "URI list"),
    ("helloworld", "helloworld", Set.empty[String]),
    ("cassandra", "cassandra/dcos",
      Set("https://downloads.mesosphere.io/cassandra-mesos/artifacts/0.2.0-1/cassandra-mesos-0.2.0-1.tar.gz",
          "https://downloads.mesosphere.io/java/jre-7u76-linux-x64.tar.gz"))
  )

  private def getPackageUris(appId: String): Future[Set[String]] = {
    adminRouter.getApp(appId) map { response =>
      val Right(parsed) = parse(response.contentString)
      parsed.cursor
        .downField("app")
        .flatMap(_.downField("uris"))
        .flatMap(_.as[Set[String]].toOption)
        .getOrElse(Set())
    }
  }

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
    val actualLabel = Await.result(getMarathonJsonTestLabel(packageName))
    typedAssertResult(expectedLabel)(actualLabel)
  }

  private def getMarathonJsonTestLabel(packageName: String): Future[Option[String]] = {
    adminRouter.getApp(Uri.parse(packageName)) map { case response =>
      val Right(parsed) = parse(response.contentString)
      val option = parsed.cursor.downField("app").flatMap(extractTestLabel)
      option
    }
  }

  private def extractTestLabel(marathonJsonCursor: Cursor): Option[String] = {
    marathonJsonCursor
      .downField("labels")
      .flatMap(_.downField("test-id"))
      .flatMap(_.as[String].toOption)
  }

  private def withTempDirectory[A](f: Path => A): A = {
    val tempDir = Files.createTempDirectory("cosmos")
    Try(f(tempDir)).ensure {
      val visitor = new SimpleFileVisitor[Path] {

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
          Option(e) match {
            case Some(failure) => throw failure
            case _ =>
              Files.delete(dir)
              FileVisitResult.CONTINUE
          }
        }

      }

      val _ = Files.walkFileTree(tempDir, visitor)
    }.get()
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
    postInstallState: PostInstallState,
    appIdOpt: Option[String] = None
  ): Unit = {

    val appId = appIdOpt.getOrElse(packageName)
    val packageWasInstalled = isAppInstalled(appId)
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
    val actuallyInstalled = isAppInstalled(appId)
    typedAssertResult(expectedInstalled)(actuallyInstalled)
  }

  private[this] def isAppInstalled(appId: String): Boolean = {
    Await.result(listMarathonAppIds()).contains(s"/$appId")
  }

  private[this] def listMarathonAppIds(): Future[Seq[String]] = {
    adminRouter.listApps() map { case response =>
      val Right(jsonContent) = parse(response.contentString)
      val Right(appCursors) = jsonContent.cursor.get[List[Json]]("apps")
      val Right(appIds) = appCursors.traverseU(_.cursor.get[String]("id"))
      appIds
    }
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
