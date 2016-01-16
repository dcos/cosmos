package com.mesosphere.cosmos

import cats.data.Xor
import cats.data.Xor.Right
import cats.std.list._
import cats.syntax.traverse._
import com.mesosphere.cosmos.model.InstallRequest
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.http._
import com.twitter.finagle.{Http, Service}
import com.twitter.io.{Buf, Charsets}
import com.twitter.util._
import io.circe.generic.auto._
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Cursor, Json}
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.{Base64, UUID}

final class PackageInstallSpec extends FreeSpec with BeforeAndAfterAll with CosmosSpec {

  import PackageInstallSpec._

  "The package install endpoint" - {

    "can successfully deploy a service to Marathon" in {
      runService() { apiClient =>
        forAll (PackageTable) { (packageName, packageJson) =>
          apiClient.installPackageAndAssert(
            packageName,
            Status.Ok,
            content = Json.empty,
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
            Status.BadRequest,
            content = errorJson(s"Package [$packageName] not found"),
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
              content = errorJson("Package is already installed"),
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
                content = errorJson(s"Received response status code ${status.code} from Marathon"),
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
                content = errorJson(s"Received response status code ${status.code} from Marathon"),
                preInstallState = Anything,
                postInstallState = Unchanged
              )
            }
          }
        }
      }
    }

    "can successfully install packages from Universe" in {
      val _ = withTempDirectory { universeDir =>
        val universeCache = Await.result(UniversePackageCache(UniverseUri, universeDir))

        runService(packageCache = universeCache) { apiClient =>
          forAll (UniversePackagesTable) { (packageName, appId, uriSet, labelsOpt, versionOpt) =>
            apiClient.installPackageAndAssert(
              packageName,
              Status.Ok,
              content = Json.empty,
              preInstallState = NotInstalled,
              postInstallState = Installed,
              appIdOpt = Some(appId),
              versionOpt
            )
            // TODO Confirm that the correct config was sent to Marathon - see issue #38
            val packageInfo = Await.result(getPackageInfo(appId))
            assertResult(uriSet)(packageInfo.uris)
            labelsOpt.foreach(labels => assertResult(labels)(StandardLabels(packageInfo.labels)))
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

  private val HelloWorldLabels = StandardLabels(
    Map(
      "DCOS_PACKAGE_METADATA" ->
        ("eyJkZXNjcmlwdGlvbiI6ICJFeGFtcGxlIERDT1MgYXBwbGljYXRpb24gcGFja2FnZSIsICJtYWludGFpbmVyIjogIn" +
        "N1cHBvcnRAbWVzb3NwaGVyZS5pbyIsICJuYW1lIjogImhlbGxvd29ybGQiLCAicG9zdEluc3RhbGxOb3RlcyI6IC" +
        "JBIHNhbXBsZSBwb3N0LWluc3RhbGxhdGlvbiBtZXNzYWdlIiwgInByZUluc3RhbGxOb3RlcyI6ICJBIHNhbXBsZS" +
        "BwcmUtaW5zdGFsbGF0aW9uIG1lc3NhZ2UiLCAidGFncyI6IFsibWVzb3NwaGVyZSIsICJleGFtcGxlIiwgInN1Ym" +
        "NvbW1hbmQiXSwgInZlcnNpb24iOiAiMC4xLjAiLCAid2Vic2l0ZSI6ICJodHRwczovL2dpdGh1Yi5jb20vbWVzb3" +
        "NwaGVyZS9kY29zLWhlbGxvd29ybGQifQ=="),
      "DCOS_PACKAGE_COMMAND" ->
        ("eyJwaXAiOiBbImRjb3M8MS4wIiwgImdpdCtodHRwczovL2dpdGh1Yi5jb20vbWVzb3NwaGVyZS9kY29zLWhlbGxvd2" +
        "9ybGQuZ2l0I2Rjb3MtaGVsbG93b3JsZD0wLjEuMCJdfQ=="),
      "DCOS_PACKAGE_REGISTRY_VERSION" -> "2.0.0-rc1",
      "DCOS_PACKAGE_NAME" -> "helloworld",
      "DCOS_PACKAGE_VERSION" -> "0.1.0",
      "DCOS_PACKAGE_SOURCE" -> "https://github.com/mesosphere/universe/archive/cli-test-3.zip",
      "DCOS_PACKAGE_RELEASE" -> "0"
    )
  )

  private val CassandraUris = Set(
    "https://downloads.mesosphere.io/cassandra-mesos/artifacts/0.2.0-1/cassandra-mesos-0.2.0-1.tar.gz",
    "https://downloads.mesosphere.io/java/jre-7u76-linux-x64.tar.gz"
  )

  private val UniversePackagesTable = Table(
    ("package name", "app id", "URI list", "Labels", "version"),
    ("helloworld", "helloworld", Set.empty[String], Some(HelloWorldLabels), None),
    ("cassandra", "cassandra/dcos", CassandraUris, None, Some("0.2.0-1"))
  )

  private def getPackageInfo(appId: String): Future[PackageInfo] = {
    adminRouter.getApp(appId) map { response =>
      val Right(parsed) = parse(response.contentString)
      val baseCursor = parsed.cursor.downField("app")

      val uris = baseCursor
        .flatMap(_.downField("uris"))
        .flatMap(_.as[Set[String]].toOption)
        .getOrElse(Set())

      val labels = baseCursor
        .flatMap(_.downField("labels"))
        .flatMap(_.as[Map[String, String]].toOption)
        .getOrElse(Map.empty)

      PackageInfo(uris, labels)
    }
  }

  private lazy val PackageMap: Map[String, Json] = PackageTableRows.toMap

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

  private def errorJson(message: String): Json = {
    Map("errors" -> Seq(Map("message" -> message))).asJson
  }

  private def assertPackageInstalledFromCache(packageName: String, packageJson: Json): Unit = {
    val expectedLabel = extractTestLabel(packageJson.cursor)
    val actualLabel = Await.result(getMarathonJsonTestLabel(packageName))
    assertResult(expectedLabel)(actualLabel)
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

private final class ApiTestAssertionDecorator(apiClient: Service[Request, Response]) extends CosmosSpec {

  import ApiTestAssertionDecorator._

  private[cosmos] def installPackageAndAssert(
    packageName: String,
    status: Status,
    content: Json,
    preInstallState: PreInstallState,
    postInstallState: PostInstallState,
    appIdOpt: Option[String] = None,
    version: Option[String] = None
  ): Unit = {

    val appId = appIdOpt.getOrElse(packageName)
    val packageWasInstalled = isAppInstalled(appId)
    preInstallState match {
      case AlreadyInstalled => assertResult(true)(packageWasInstalled)
      case NotInstalled => assertResult(false)(packageWasInstalled)
      case Anything => // Don't care
    }

    val response = installPackage(apiClient, packageName, version)
    assertResult(status)(response.status)
    assertResult(Xor.Right(content))(parse(response.contentString))

    val expectedInstalled = postInstallState match {
      case Installed => true
      case Unchanged => packageWasInstalled
    }
    val actuallyInstalled = isAppInstalled(appId)
    assertResult(expectedInstalled)(actuallyInstalled)
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
    apiClient: Service[Request, Response], packageName: String, version: Option[String]
  ): Response = {
    val installRequest = requestBuilder(InstallEndpoint)
      .buildPost(Buf.Utf8(InstallRequest(packageName, version).asJson.noSpaces))
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

case class PackageInfo(uris: Set[String], labels: Map[String, String])

case class StandardLabels(
  packageMetadata: Json,
  packageCommand: Json,
  packageRegistryVersion: String,
  packageName: String,
  packageVersion: String,
  packageSource: String,
  packageRelease: String
)

object StandardLabels {

  def apply(labels: Map[String, String]): StandardLabels = {
    StandardLabels(
      packageMetadata = decodeAndParse(labels("DCOS_PACKAGE_METADATA")),
      packageCommand = decodeAndParse(labels("DCOS_PACKAGE_COMMAND")),
      packageRegistryVersion = labels("DCOS_PACKAGE_REGISTRY_VERSION"),
      packageName = labels("DCOS_PACKAGE_NAME"),
      packageVersion = labels("DCOS_PACKAGE_VERSION"),
      packageSource = labels("DCOS_PACKAGE_SOURCE"),
      packageRelease = labels("DCOS_PACKAGE_RELEASE")
    )
  }

  private[this] def decodeAndParse(encoded: String): Json = {
    val decoded = new String(Base64.getDecoder.decode(encoded), Charsets.Utf8)
    val Right(parsed) = parse(decoded)
    parsed
  }

}
