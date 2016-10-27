package com.mesosphere.cosmos

import cats.data.Xor
import cats.data.Xor.Right
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.{ErrorResponse, RunRequest, RunResponse}
import com.mesosphere.cosmos.rpc.v2.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model._
import com.mesosphere.universe
import com.mesosphere.universe.v2.model.{PackageDetails, PackageDetailsVersion, PackageFiles, PackagingVersion}
import com.netaporter.uri.Uri
import com.twitter.finagle.http._
import com.twitter.io.{Buf, Charsets}
import com.twitter.util.{Await, Future}
import io.circe.jawn._
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import java.util.{Base64, UUID}

final class PackageRunIntegrationSpec extends FreeSpec with BeforeAndAfterAll {

  import CosmosIntegrationTestClient._
  import PackageRunIntegrationSpec._

  "The package run endpoint" - {

    "reports an error if the requested package is not in the cache" in {
      forAll (PackageTable) { (packageName, _) =>
        val errorResponse = ErrorResponse(
          `type` = "PackageNotFound",
          message = s"Package [$packageName] not found",
          data = Some(JsonObject.singleton("packageName", packageName.asJson))
        )

        runPackageAndAssert(
          RunRequest(packageName),
          expectedResult = RunFailure(Status.BadRequest, errorResponse),
          preRunState = Anything,
          postRunState = Unchanged
        )
      }
    }

    "don't run if specified version is not found" in {
      forAll (PackageDummyVersionsTable) { (packageName, packageVersion) =>
        val errorMessage = s"Version [$packageVersion] of package [$packageName] not found"
        val errorResponse = ErrorResponse(
          `type` = "VersionNotFound",
          message = errorMessage,
          data = Some(JsonObject.fromMap(Map(
            "packageName" -> packageName.asJson,
            "packageVersion" -> packageVersion.toString.asJson
          )))
        )

        // TODO This currently relies on test execution order to be correct
        // Update it to explicitly run a package twice
        runPackageAndAssert(
          RunRequest(packageName, packageVersion = Some(packageVersion)),
          expectedResult = RunFailure(Status.BadRequest, errorResponse),
          preRunState = NotRunning,
          postRunState = Unchanged
        )
      }
    }

    "can successfully run packages from Universe" in {
      forAll (UniversePackagesTable) { (expectedResponse, forceVersion, uriSet, labelsOpt) =>
        val versionOption = if (forceVersion) Some(expectedResponse.packageVersion) else None

        runPackageAndAssert(
          RunRequest(expectedResponse.packageName, packageVersion = versionOption),
          expectedResult = RunSuccess(expectedResponse),
          preRunState = NotRunning,
          postRunState = Running
        )
        // TODO Confirm that the correct config was sent to Marathon - see issue #38
        val packageInfo = Await.result(getMarathonApp(expectedResponse.appId))
        assertResult(uriSet)(packageInfo.uris.toSet)
        labelsOpt.foreach(labels => assertResult(labels)(StandardLabels(packageInfo.labels)))

        // Assert that running twice gives us a package already running error
        runPackageAndAssert(
          RunRequest(expectedResponse.packageName, packageVersion = versionOption),
          expectedResult = RunFailure(
            Status.Conflict,
            ErrorResponse(
              "PackageAlreadyRunning",
              "Package is already running",
              Some(JsonObject.empty)
            ),
            Some(expectedResponse.appId)
          ),
          preRunState = AlreadyRunning,
          postRunState = Unchanged
        )
      }
    }

    "supports custom app IDs" in {
      val expectedResponse = RunResponse("cassandra", PackageDetailsVersion("0.2.0-2"), AppId("custom-app-id"))

      runPackageAndAssert(
        RunRequest(
          packageName = expectedResponse.packageName,
          packageVersion = Some(PackageDetailsVersion("0.2.0-2")),
          appId = Some(expectedResponse.appId)
        ),
        expectedResult = RunSuccess(expectedResponse),
        preRunState = NotRunning,
        postRunState = Running
      )
    }

    "validates merged config template options JSON schema" in {
      val Some(badOptions) = Map("chronos" -> Map("zk-hosts" -> false)).asJson.asObject

      val schemaError = JsonObject.fromIterable {
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

      val appId = AppId("chronos-bad-json")

      runPackageAndAssert(
        RunRequest("chronos", options = Some(badOptions), appId = Some(appId)),
        expectedResult = RunFailure(Status.BadRequest, errorResponse),
        preRunState = Anything,
        postRunState = Unchanged
      )
    }

    "will error if attempting to run a service for a v1 response with no marathon template" in {
      val errorResponse = ErrorResponse(
        "ServiceMarathonTemplateNotFound",
        s"Package: [enterprise-security-cli] version: [0.8.0] does not have a " +
          "Marathon template defined and can not be rendered",
        Some(JsonObject.fromMap(Map(
          "packageName" -> "enterprise-security-cli".asJson,
          "packageVersion" -> "0.8.0".asJson
        )))
      )

      runPackageAndAssert(
        RunRequest("enterprise-security-cli"),
        expectedResult = RunFailure(Status.BadRequest, errorResponse),
        preRunState = Anything,
        postRunState = Unchanged
      )
    }

    "will succeed if attempting to run a service for a v2 response with no marathon template" in {
      import com.mesosphere.universe.v3.model._
      val expectedBody = rpc.v2.model.RunResponse(
        "enterprise-security-cli",
        universe.v3.model.PackageDefinition.Version("0.8.0"),
        cli = Some(Cli(Some(Platforms(
          None,
          Some(Architectures(Binary(
            "zip",
            "https://downloads.dcos.io/cli/binaries/linux/x86-64/0.8.0/dcos-security",
            List(HashInfo("sha256","6e745248badc4741048a52a9d0ee6eabd4ddda239dfe345f7ba8397535ce3f3d"))
          ))),
          Some(Architectures(Binary(
            "zip",
            "https://downloads.dcos.io/cli/binaries/darwin/x86-64/0.8.0/dcos-security",
            List(HashInfo("sha256","8f486a771e4db8c72725742102f384274cc028fee9d58fe92268e38f1c1adbeb"))
          )))
        ))))
      )

      val runRequest = RunRequest("enterprise-security-cli")

      val request = CosmosClient.requestBuilder("package/run")
        .addHeader("Content-Type", MediaTypes.RunRequest.show)
        .addHeader("Accept", MediaTypes.V2RunResponse.show)
        .buildPost(Buf.Utf8(runRequest.asJson.noSpaces))

      val response = CosmosClient(request)

      assertResult(Status.Ok)(response.status)
      assertResult(MediaTypes.V2RunResponse.show)(response.contentType.get)
      val Xor.Right(actualBody) = decode[rpc.v2.model.RunResponse](response.contentString)
      assertResult(expectedBody)(actualBody)
    }

  }

  override protected def beforeAll(): Unit = { /*no-op*/ }

  override protected def afterAll(): Unit = {
    // TODO: This should actually happen between each test, but for now tests depend on eachother :(
    val deletes: Future[Seq[Unit]] = Future.collect(Seq(
      adminRouter.deleteApp(AppId("/helloworld"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/cassandra/dcos"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/custom-app-id"), force = true) map { resp => assert(resp.getStatusCode() === 200) },

      // Make sure this is cleaned up if its test failed
      adminRouter.deleteApp(AppId("/chronos-bad-json"), force = true) map { _ => () }
    ))
    Await.result(deletes.flatMap { x => Future.Unit })
  }

  private[cosmos] def runPackageAndAssert(
    runRequest: RunRequest,
    expectedResult: ExpectedResult,
    preRunState: PreRunState,
    postRunState: PostRunState
  ): Unit = {
    val appId = expectedResult.appId.getOrElse(AppId(runRequest.packageName))

    val packageWasRunning = isAppRunning(appId)
    preRunState match {
      case AlreadyRunning => assertResult(true)(packageWasRunning)
      case NotRunning => assertResult(false)(packageWasRunning)
      case Anything => // Don't care
    }

    val response = runPackage(runRequest)

    assertResult(expectedResult.status)(response.status)
    expectedResult match {
      case RunSuccess(expectedBody) =>
        val Xor.Right(actualBody) = decode[RunResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
      case RunFailure(_, expectedBody, _) =>
        val Xor.Right(actualBody) = decode[ErrorResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
    }

    val expectedRunning = postRunState match {
      case Running => true
      case Unchanged => packageWasRunning
    }
    val actuallyRunning = isAppRunning(appId)
    assertResult(expectedRunning)(actuallyRunning)
  }

  private[this] def isAppRunning(appId: AppId): Boolean = {
    Await.result {
      adminRouter.getAppOption(appId)
        .map(_.isDefined)
    }
  }

  private[this] def runPackage(
    runRequest: RunRequest
  ): Response = {
    val request = CosmosClient.requestBuilder("package/run")
      .addHeader("Content-Type", MediaTypes.RunRequest.show)
      .addHeader("Accept", MediaTypes.V1RunResponse.show)
      .buildPost(Buf.Utf8(runRequest.asJson.noSpaces))
    CosmosClient(request)
  }

}

private object PackageRunIntegrationSpec extends Matchers with TableDrivenPropertyChecks {

  private val PackageTableRows: Seq[(String, PackageFiles)] = Seq(
    packageTableRow("helloworld2", 1, 512.0, 2),
    packageTableRow("helloworld3", 0.75, 256.0, 3)
  )

  private lazy val PackageTable = Table(
    ("package name", "package files"),
    PackageTableRows: _*
  )

  private val PackageDummyVersionsTable = Table(
    ("package name", "version"),
    ("helloworld", PackageDetailsVersion("a.b.c")),
    ("cassandra", PackageDetailsVersion("foobar"))
  )

  private val HelloWorldCommand: Json = Map(
    "pip" -> List(
      "dcos<1.0".asJson,
      "git+https://github.com/mesosphere/dcos-helloworld.git#dcos-helloworld=0.1.0".asJson
    ).asJson
  ).asJson

  private val HelloWorldLabels = StandardLabels(
    packageMetadata = Map(
      "website" -> "https://github.com/mesosphere/dcos-helloworld".asJson,
      "name" -> "helloworld".asJson,
      "postInstallNotes" -> "A sample post-installation message".asJson,
      "description" -> "Example DCOS application package".asJson,
      "packagingVersion" -> "2.0".asJson,
      "tags" -> List("mesosphere".asJson, "example".asJson, "subcommand".asJson).asJson,
      "selected" -> false.asJson,
      "maintainer" -> "support@mesosphere.io".asJson,
      "version" -> "0.1.0".asJson,
      "preInstallNotes" -> "A sample pre-installation message".asJson
    ).asJson,
    packageCommand = HelloWorldCommand,
    packageRegistryVersion = "2.0",
    packageName = "helloworld",
    packageVersion = "0.1.0",
    packageSource = DefaultRepositories().getOrThrow(1).uri.toString,
    packageRelease = "0"
  )

  private val CassandraUris = Set(
    "https://downloads.mesosphere.com/cassandra-mesos/artifacts/0.2.0-1/cassandra-mesos-0.2.0-1.tar.gz",
    "https://downloads.mesosphere.com/java/jre-7u76-linux-x64.tar.gz"
  )

  private val UniversePackagesTable = Table(
    ("expected response", "force version", "URI list", "Labels"),
    (RunResponse("helloworld", PackageDetailsVersion("0.1.0"), AppId("helloworld")), false, Set.empty[String], Some(HelloWorldLabels)),
    (RunResponse("cassandra", PackageDetailsVersion("0.2.0-1"), AppId("cassandra/dcos")), true, CassandraUris, None)
  )

  private def getMarathonApp(appId: AppId)(implicit session: RequestSession): Future[MarathonApp] = {
    CosmosIntegrationTestClient.adminRouter.getApp(appId)
      .map(_.app)
  }

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
          network = Some("HOST")
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
}

private sealed abstract class ExpectedResult(val status: Status, val appId: Option[AppId])

private case class RunSuccess(body: RunResponse)
  extends ExpectedResult(Status.Ok, Some(body.appId))

private case class RunFailure(
  override val status: Status,
  body: ErrorResponse,
  override val appId: Option[AppId] = None
) extends ExpectedResult(status, appId)

private sealed trait PreRunState
private case object AlreadyRunning extends PreRunState
private case object NotRunning extends PreRunState
private case object Anything extends PreRunState

private sealed trait PostRunState
private case object Running extends PostRunState
private case object Unchanged extends PostRunState

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
