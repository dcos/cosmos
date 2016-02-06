package com.mesosphere.cosmos

import java.util.{Base64, UUID}

import cats.data.Xor
import cats.data.Xor.Right
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonApp, MarathonAppContainer, MarathonAppContainerDocker}
import com.mesosphere.universe.{PackageDetails, PackageDetailsVersion, PackageFiles, PackagingVersion}
import com.netaporter.uri.Uri
import com.twitter.finagle.http._
import com.twitter.finagle.{Http, Service}
import com.twitter.io.{Buf, Charsets}
import com.twitter.util.{Await, Future}
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Cursor, Json, JsonObject}
import io.finch.circe._
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

final class PackageInstallSpec extends FreeSpec with BeforeAndAfterAll with CosmosSpec {

  import IntegrationHelpers._
  import PackageInstallSpec._

  "The package install endpoint" - {

    "can successfully deploy a service to Marathon" in {
      runService() { apiClient =>
        forAll (PackageTable) { (packageName, packageFiles) =>
          val appId = AppId(packageName)

          apiClient.installPackageAndAssert(
            InstallRequest(packageName),
            expectedResult = Success(
              InstallResponse(packageName, packageFiles.packageJson.version, appId)),
            preInstallState = NotInstalled,
            postInstallState = Installed
          )

          assertPackageInstalledFromCache(appId, packageFiles)

          // TODO: Uninstall the package, or use custom Marathon app IDs to avoid name clashes
        }
      }
    }

    "reports an error if the requested package is not in the cache" in {
      forAll (PackageTable) { (packageName, _) =>
        val packageCache = MemoryPackageCache(PackageMap - packageName)
        val errorResponse = ErrorResponse("PackageNotFound", s"Package [$packageName] not found")

        runService(packageCache = packageCache) { apiClient =>
          apiClient.installPackageAndAssert(
            InstallRequest(packageName),
            expectedResult = Failure(Status.BadRequest, errorResponse),
            preInstallState = Anything,
            postInstallState = Unchanged
          )
        }
      }
    }

    "reports an error if the request to Marathon fails" - {

      "due to the package already being installed" in {
        val errorResponse = ErrorResponse("PackageAlreadyInstalled", "Package is already installed")
        runService() { apiClient =>
          forAll (PackageTable) { (packageName, _) =>
            // TODO This currently relies on test execution order to be correct
            // Update it to explicitly install a package twice
            apiClient.installPackageAndAssert(
              InstallRequest(packageName),
              expectedResult = Failure(Status.Conflict, errorResponse),
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
          val errorMessage = s"Received response status code ${status.code} from Marathon"
          val errorResponse = ErrorResponse("MarathonBadResponse", errorMessage)

          runService(dcosClient = dcosClient) { apiClient =>
            forAll(PackageTable) { (packageName, _) =>
              apiClient.installPackageAndAssert(
                InstallRequest(packageName),
                expectedResult = Failure(Status.InternalServerError, errorResponse),
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
          val errorMessage = s"Received response status code ${status.code} from Marathon"
          val errorResponse = ErrorResponse("MarathonBadGateway", errorMessage)

          runService(dcosClient = dcosClient) { apiClient =>
            forAll (PackageTable) { (packageName, _) =>
              apiClient.installPackageAndAssert(
                InstallRequest(packageName),
                expectedResult = Failure(Status.BadGateway, errorResponse),
                preInstallState = Anything,
                postInstallState = Unchanged
              )
            }
          }
        }
      }
    }

    "don't install if specified version is not found" in {
      val _ = withTempDirectory { universeDir =>
        val universeCache = UniversePackageCache(UniverseUri, universeDir)

        runService(packageCache = universeCache) { apiClient =>
          forAll (PackageDummyVersionsTable) { (packageName, packageVersion) =>
            val errorMessage = s"Version [$packageVersion] of package [$packageName] not found"
            val errorResponse = ErrorResponse("VersionNotFound", errorMessage)

            // TODO This currently relies on test execution order to be correct
            // Update it to explicitly install a package twice
            apiClient.installPackageAndAssert(
              InstallRequest(packageName, packageVersion = Some(packageVersion)),
              expectedResult = Failure(Status.BadRequest, errorResponse),
              preInstallState = NotInstalled,
              postInstallState = Unchanged
            )
          }
        }
      }
    }

    "can successfully install packages from Universe" in {
      val _ = withTempDirectory { universeDir =>
        val universeCache = UniversePackageCache(UniverseUri, universeDir)

        runService(packageCache = universeCache) { apiClient =>
          forAll (UniversePackagesTable) { (expectedResponse, forceVersion, uriSet, labelsOpt) =>
            val versionOption = if (forceVersion) Some(expectedResponse.packageVersion) else None

            apiClient.installPackageAndAssert(
              InstallRequest(expectedResponse.packageName, packageVersion = versionOption),
              expectedResult = Success(expectedResponse),
              preInstallState = NotInstalled,
              postInstallState = Installed
            )
            // TODO Confirm that the correct config was sent to Marathon - see issue #38
            val packageInfo = Await.result(getMarathonApp(expectedResponse.appId))
            assertResult(uriSet)(packageInfo.uris.toSet)
            labelsOpt.foreach(labels => assertResult(labels)(StandardLabels(packageInfo.labels)))
          }
        }
      }
    }

    "supports custom app IDs" in {
      val _ = withTempDirectory { universeDir =>
        val universeCache = UniversePackageCache(UniverseUri, universeDir)

        runService(packageCache = universeCache) { apiClient =>
          val expectedResponse = InstallResponse("cassandra", PackageDetailsVersion("0.2.0-1"), AppId("custom-app-id"))

          apiClient.installPackageAndAssert(
            InstallRequest(expectedResponse.packageName, appId = Some(expectedResponse.appId)),
            expectedResult = Success(expectedResponse),
            preInstallState = NotInstalled,
            postInstallState = Installed
          )
        }
      }
    }

    "validates merged config template options JSON schema" in {
      val Some(badOptions) = Map("chronos" -> Map("zk-hosts" -> false)).asJson.asObject

      val schemaError = JsonObject.fromIndexedSeq {
        Vector(
          "level" -> "error".asJson,
          "schema" -> Map(
            "loadingURI" -> "#",
            "pointer" -> "/properties/chronos/properties/zk-hosts"
          ).asJson,
          "instance" -> Map("pointer" -> "/chronos/zk-hosts").asJson,
          "domain" -> "validation".asJson,
          "keyword" -> "type".asJson,
          "message" -> "instance type (boolean) does not match any allowed primitive type (allowed: [\"string\"])".asJson,
          "found" -> "boolean".asJson,
          "expected" -> List("string").asJson
        )
      }.asJson

      val errorData = JsonObject.singleton("errors", List(schemaError).asJson)
      val errorResponse =
        ErrorResponse("JsonSchemaMismatch", "Options JSON failed validation", Some(errorData))

      val _ = withTempDirectory { universeDir =>
        val universeCache = UniversePackageCache(UniverseUri, universeDir)

        runService(packageCache = universeCache) { apiClient =>
          val appId = AppId("chronos-bad-json")

          apiClient.installPackageAndAssert(
            InstallRequest("chronos", options = Some(badOptions), appId = Some(appId)),
            expectedResult = Failure(Status.BadRequest, errorResponse),
            preInstallState = Anything,
            postInstallState = Unchanged
          )
        }
      }
    }

  }

  override protected def beforeAll(): Unit = { /*no-op*/ }

  override protected def afterAll(): Unit = {
    // TODO: This should actually happen between each test, but for now tests depend on eachother :(
    val deletes: Future[Seq[Unit]] = Future.collect(Seq(
      adminRouter.deleteApp(AppId("/helloworld"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/helloworld2"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/helloworld3"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/cassandra/dcos"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/custom-app-id"), force = true) map { resp => assert(resp.getStatusCode() === 200) },

      // Make sure this is cleaned up if its test failed
      adminRouter.deleteApp(AppId("/chronos-bad-json"), force = true) map { _ => () }
    ))
    Await.result(deletes.flatMap { x => Future.Unit })
  }

  private[this] def runService[A](
    dcosClient: Service[Request, Response] = Services.adminRouterClient(adminRouterHost).get,
    packageCache: PackageCache = MemoryPackageCache(PackageMap)
  )(
    f: ApiTestAssertionDecorator => Unit
  ): Unit = {
    val adminRouter = new AdminRouter(adminRouterHost, dcosClient)
    // these two imports provide the implicit DecodeRequest instances needed to instantiate Cosmos
    val marathonPackageRunner = new MarathonPackageRunner(adminRouter)
    //TODO: Get rid of this duplication
    val service = new Cosmos(
      packageCache,
      marathonPackageRunner,
      new UninstallHandler(adminRouter, packageCache),
      new PackageInstallHandler(packageCache, marathonPackageRunner),
      new PackageRenderHandler(packageCache),
      new PackageSearchHandler(packageCache),
      new PackageImportHandler,
      new PackageDescribeHandler(packageCache),
      new ListVersionsHandler(packageCache),
      new ListHandler(adminRouter, packageCache),
      CapabilitiesHandler()
    ).service
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

  private val UniverseUri = Uri.parse("https://github.com/mesosphere/universe/archive/cli-test-4.zip")

  private val PackageTableRows: Seq[(String, PackageFiles)] = Seq(
    packageTableRow("helloworld2", 1, 512.0, 2),
    packageTableRow("helloworld3", 0.75, 256.0, 3)
  )

  private lazy val PackageTable = Table(
    ("package name", "package files"),
    PackageTableRows: _*
  )

  private val HelloWorldLabels = StandardLabels(
    Map(
      ("DCOS_PACKAGE_METADATA", "eyJ3ZWJzaXRlIjoiaHR0cHM6Ly9naXRodWIuY29tL21lc29zcGhlcmUvZGNvcy1" +
        "oZWxsb3dvcmxkIiwibmFtZSI6ImhlbGxvd29ybGQiLCJwb3N0SW5zdGFsbE5vdGVzIjoiQSBzYW1wbGUgcG9zdC" +
        "1pbnN0YWxsYXRpb24gbWVzc2FnZSIsImRlc2NyaXB0aW9uIjoiRXhhbXBsZSBEQ09TIGFwcGxpY2F0aW9uIHBhY" +
        "2thZ2UiLCJwYWNrYWdpbmdWZXJzaW9uIjoiMi4wIiwidGFncyI6WyJtZXNvc3BoZXJlIiwiZXhhbXBsZSIsInN1" +
        "YmNvbW1hbmQiXSwibWFpbnRhaW5lciI6InN1cHBvcnRAbWVzb3NwaGVyZS5pbyIsInZlcnNpb24iOiIwLjEuMCI" +
        "sInByZUluc3RhbGxOb3RlcyI6IkEgc2FtcGxlIHByZS1pbnN0YWxsYXRpb24gbWVzc2FnZSJ9"),
      ("DCOS_PACKAGE_COMMAND", "eyJwaXAiOlsiZGNvczwxLjAiLCJnaXQraHR0cHM6Ly9naXRodWIuY29tL21lc29z" +
        "cGhlcmUvZGNvcy1oZWxsb3dvcmxkLmdpdCNkY29zLWhlbGxvd29ybGQ9MC4xLjAiXX0="),
      "DCOS_PACKAGE_REGISTRY_VERSION" -> "2.0",
      "DCOS_PACKAGE_NAME" -> "helloworld",
      "DCOS_PACKAGE_VERSION" -> "0.1.0",
      "DCOS_PACKAGE_SOURCE" -> "https://github.com/mesosphere/universe/archive/cli-test-4.zip",
      "DCOS_PACKAGE_RELEASE" -> "0"
    )
  )

  private val CassandraUris = Set(
    "https://downloads.mesosphere.com/cassandra-mesos/artifacts/0.2.0-1/cassandra-mesos-0.2.0-1.tar.gz",
    "https://downloads.mesosphere.com/java/jre-7u76-linux-x64.tar.gz"
  )

  private val UniversePackagesTable = Table(
    ("expected response", "force version", "URI list", "Labels"),
    (InstallResponse("helloworld", PackageDetailsVersion("0.1.0"), AppId("helloworld")), false, Set.empty[String], Some(HelloWorldLabels)),
    (InstallResponse("cassandra", PackageDetailsVersion("0.2.0-1"), AppId("cassandra/dcos")), true, CassandraUris, None)
  )

  private val PackageDummyVersionsTable = Table(
    ("package name", "version"),
    ("helloworld", PackageDetailsVersion("a.b.c")),
    ("cassandra", PackageDetailsVersion("foobar"))
  )

  private def getMarathonApp(appId: AppId): Future[MarathonApp] = {
    adminRouter.getApp(appId)
      .map(_.app)
  }

  private lazy val PackageMap: Map[String, PackageFiles] = PackageTableRows.toMap

  private def packageTableRow(
    name: String, cpus: Double, mem: Double, pythonVersion: Int
  ): (String, PackageFiles) = {
    val cmd =
      if (pythonVersion <= 2) "python2 -m SimpleHTTPServer 8082" else "python3 -m http.server 8083"

    val packageDefinition = PackageDetails(
      packagingVersion = PackagingVersion("2.0"),
      name = name,
      version = PackageDetailsVersion("0.1.0"),
      maintainer = "Mesosphere",
      description = "Test framework",
      tags = Nil,
      scm = None,
      website = None,
      framework = None,
      preInstallNotes = None,
      postInstallNotes = None,
      postUninstallNotes = None,
      licenses = None
    )

    val marathonJson = MarathonApp(
      id = AppId(name),
      cpus = cpus,
      mem = mem,
      instances = 1,
      cmd = Some(cmd),
      container = Some(MarathonAppContainer(
        `type` = "DOCKER",
        docker = Some(MarathonAppContainerDocker(
          image = s"python:$pythonVersion",
          network = "HOST"
        ))
      )),
      labels = Map("test-id" -> UUID.randomUUID().toString),
      uris = List.empty
    )

    val packageFiles = PackageFiles(
      revision = "0",
      sourceUri = Uri.parse("in/memory/source"),
      packageJson = packageDefinition,
      marathonJsonMustache = marathonJson.asJson.noSpaces
    )

    (name, packageFiles)
  }

  private def assertPackageInstalledFromCache(appId: AppId, packageFiles: PackageFiles): Unit = {
    val Right(marathonJson) = parse(packageFiles.marathonJsonMustache)
    val expectedLabel = extractTestLabel(marathonJson.cursor)
    val actualLabel = Await.result(getMarathonJsonTestLabel(appId))
    assertResult(expectedLabel)(actualLabel)
  }

  private def getMarathonJsonTestLabel(appId: AppId): Future[Option[String]] = {
    adminRouter.getApp(appId) map {
      _.app.labels.get("test-id")
    }
  }

  private def extractTestLabel(marathonJsonCursor: Cursor): Option[String] = {
    marathonJsonCursor
      .downField("labels")
      .flatMap(_.downField("test-id"))
      .flatMap(_.as[String].toOption)
  }

}

private final class ApiTestAssertionDecorator(apiClient: Service[Request, Response]) extends CosmosSpec {

  import ApiTestAssertionDecorator._

  private[cosmos] def installPackageAndAssert(
    installRequest: InstallRequest,
    expectedResult: ExpectedResult,
    preInstallState: PreInstallState,
    postInstallState: PostInstallState
  ): Unit = {
    val appId = expectedResult.appId.getOrElse(AppId(installRequest.packageName))

    val packageWasInstalled = isAppInstalled(appId)
    preInstallState match {
      case AlreadyInstalled => assertResult(true)(packageWasInstalled)
      case NotInstalled => assertResult(false)(packageWasInstalled)
      case Anything => // Don't care
    }

    val response = installPackage(apiClient, installRequest)
    logger.debug("Response status: {}", response.statusCode)
    logger.debug("Response content: {}", response.contentString)

    assertResult(expectedResult.status)(response.status)
    expectedResult match {
      case Success(expectedBody) =>
        val Xor.Right(actualBody) = decode[InstallResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
      case Failure(_, expectedBody) =>
        val Xor.Right(actualBody) = decode[ErrorResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
    }

    val expectedInstalled = postInstallState match {
      case Installed => true
      case Unchanged => packageWasInstalled
    }
    val actuallyInstalled = isAppInstalled(appId)
    assertResult(expectedInstalled)(actuallyInstalled)
  }

  private[this] def isAppInstalled(appId: AppId): Boolean = {
    Await.result {
      adminRouter.getAppOption(appId)
        .map(_.isDefined)
    }
  }

  private[this] def installPackage(
    apiClient: Service[Request, Response],
    installRequest: InstallRequest
  ): Response = {
    val request = requestBuilder(InstallEndpoint)
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8(installRequest.asJson.noSpaces))
    Await.result(apiClient(request))
  }

}

private object ApiTestAssertionDecorator {

  private val InstallEndpoint: String = "package/install"

}

private sealed abstract class ExpectedResult(val status: Status, val appId: Option[AppId])

private case class Success(body: InstallResponse)
  extends ExpectedResult(Status.Ok, Some(body.appId))

private case class Failure(override val status: Status, body: ErrorResponse)
  extends ExpectedResult(status, None)

private sealed trait PreInstallState
private case object AlreadyInstalled extends PreInstallState
private case object NotInstalled extends PreInstallState
private case object Anything extends PreInstallState

private sealed trait PostInstallState
private case object Installed extends PostInstallState
private case object Unchanged extends PostInstallState

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
