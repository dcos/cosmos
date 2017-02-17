package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.JsonObject
import _root_.io.circe.jawn._
import _root_.io.circe.syntax._
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.InstallRequest
import com.mesosphere.cosmos.rpc.v1.model.InstallResponse
import com.mesosphere.cosmos.rpc.v2.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model._
import com.mesosphere.universe
import com.mesosphere.universe.v2.model.PackageDetails
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import com.mesosphere.universe.v2.model.PackageFiles
import com.mesosphere.universe.v2.model.PackagingVersion
import com.netaporter.uri.Uri
import com.twitter.finagle.http._
import com.twitter.util.Await
import com.twitter.util.Future
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.Succeeded
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.util.Right

final class PackageInstallIntegrationSpec extends FreeSpec with BeforeAndAfterAll {

  import CosmosIntegrationTestClient._
  import PackageInstallIntegrationSpec._

  "The package install endpoint" - {

    "reports an error if the requested package is not in the cache" in {
      forAll (PackageTable) { (packageName, _) =>
        val errorResponse = ErrorResponse(
          `type` = "PackageNotFound",
          message = s"Package [$packageName] not found",
          data = Some(JsonObject.singleton("packageName", packageName.asJson))
        )

        installPackageAndAssert(
          InstallRequest(packageName),
          expectedResult = InstallFailure(Status.BadRequest, errorResponse),
          preInstallState = Anything,
          postInstallState = Unchanged
        )
      }
    }

    "don't install if specified version is not found" in {
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
        // Update it to explicitly install a package twice
        installPackageAndAssert(
          InstallRequest(packageName, packageVersion = Some(packageVersion)),
          expectedResult = InstallFailure(Status.BadRequest, errorResponse),
          preInstallState = NotInstalled,
          postInstallState = Unchanged
        )
      }
    }

    "can successfully install packages from Universe" in {
      forAll (UniversePackagesTable) { (expectedResponse, forceVersion, uriSet, labelsOpt) =>
        val versionOption = if (forceVersion) Some(expectedResponse.packageVersion) else None

        installPackageAndAssert(
          InstallRequest(expectedResponse.packageName, packageVersion = versionOption),
          expectedResult = InstallSuccess(expectedResponse),
          preInstallState = NotInstalled,
          postInstallState = Installed
        )
        // TODO Confirm that the correct config was sent to Marathon - see issue #38
        val packageInfo = Await.result(getMarathonApp(expectedResponse.appId))
        assertResult(uriSet)(packageInfo.uris.toSet)
        labelsOpt.foreach(labels => assertResult(labels)(StandardLabels(packageInfo.labels)))

        // Assert that installing twice gives us a package already installed error
        installPackageAndAssert(
          InstallRequest(expectedResponse.packageName, packageVersion = versionOption),
          expectedResult = InstallFailure(
            Status.Conflict,
            ErrorResponse(
              "PackageAlreadyInstalled",
              "Package is already installed",
              None
            ),
            Some(expectedResponse.appId)
          ),
          preInstallState = AlreadyInstalled,
          postInstallState = Unchanged
        )
      }
    }

    "supports custom app IDs" in {
      val expectedResponse = InstallResponse("cassandra", PackageDetailsVersion("0.2.0-2"), AppId("custom-app-id"))

      installPackageAndAssert(
        InstallRequest(
          packageName = expectedResponse.packageName,
          packageVersion = Some(PackageDetailsVersion("0.2.0-2")),
          appId = Some(expectedResponse.appId)
        ),
        expectedResult = InstallSuccess(expectedResponse),
        preInstallState = NotInstalled,
        postInstallState = Installed
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

      installPackageAndAssert(
        InstallRequest("chronos", options = Some(badOptions), appId = Some(appId)),
        expectedResult = InstallFailure(Status.BadRequest, errorResponse),
        preInstallState = Anything,
        postInstallState = Unchanged
      )
    }

    "will error if attempting to install a service for a v1 response with no marathon template" in {
      val errorResponse = ErrorResponse(
        "ServiceMarathonTemplateNotFound",
        s"Package: [enterprise-security-cli] version: [0.8.0] does not have a " +
          "Marathon template defined and can not be rendered",
        Some(JsonObject.fromMap(Map(
          "packageName" -> "enterprise-security-cli".asJson,
          "packageVersion" -> "0.8.0".asJson
        )))
      )

      installPackageAndAssert(
        InstallRequest("enterprise-security-cli"),
        expectedResult = InstallFailure(Status.BadRequest, errorResponse),
        preInstallState = Anything,
        postInstallState = Unchanged
      )
    }

    "will succeed if attempting to install a service for a v2 response with no marathon template" in {
      import com.mesosphere.universe.v3.model._
      val expectedBody = rpc.v2.model.InstallResponse(
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

      val installRequest = InstallRequest("enterprise-security-cli")
      val request = CosmosRequests.packageInstallV2(installRequest)
      val response = CosmosClient.submit(request)

      assertResult(Status.Ok)(response.status)
      assertResult(MediaTypes.V2InstallResponse.show)(response.contentType.get)
      val Right(actualBody) = decode[rpc.v2.model.InstallResponse](response.contentString)
      assertResult(expectedBody)(actualBody)
    }

  }

  override protected def beforeAll(): Unit = { /*no-op*/ }

  override protected def afterAll(): Unit = {
    // TODO: This should actually happen between each test, but for now tests depend on eachother :(
    val deletes: Future[Seq[Assertion]] = Future.collect(Seq(
      adminRouter.deleteApp(AppId("/helloworld"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/cassandra/dcos"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/custom-app-id"), force = true) map { resp => assert(resp.getStatusCode() === 200) },

      // Make sure this is cleaned up if its test failed
      adminRouter.deleteApp(AppId("/chronos-bad-json"), force = true) map { _ => Succeeded }
    ))
    Await.result(deletes.flatMap { x => Future.Unit })
  }

  private[cosmos] def installPackageAndAssert(
    installRequest: InstallRequest,
    expectedResult: ExpectedResult,
    preInstallState: PreInstallState,
    postInstallState: PostInstallState
  ): Assertion = {
    val appId = expectedResult.appId.getOrElse(AppId(installRequest.packageName))

    val packageWasInstalled = isAppInstalled(appId)
    preInstallState match {
      case AlreadyInstalled => assertResult(true)(packageWasInstalled)
      case NotInstalled => assertResult(false)(packageWasInstalled)
      case Anything => // Don't care
    }

    val request = CosmosRequests.packageInstallV1(installRequest)
    val response = CosmosClient.submit(request)

    assertResult(expectedResult.status)(response.status)
    expectedResult match {
      case InstallSuccess(expectedBody) =>
        val Right(actualBody) = decode[InstallResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
      case InstallFailure(_, expectedBody, _) =>
        val Right(actualBody) = decode[ErrorResponse](response.contentString)
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

}

private object PackageInstallIntegrationSpec extends Matchers with TableDrivenPropertyChecks {

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
      "framework" -> false.asJson,
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
    (
      InstallResponse("helloworld", PackageDetailsVersion("0.1.0"), AppId("helloworld")),
      false,
      Set.empty[String],
      Some(HelloWorldLabels)
    ),
    (
      InstallResponse("cassandra", PackageDetailsVersion("0.2.0-1"), AppId("cassandra/dcos")),
      true,
      CassandraUris,
      None
    )
  )

  private def getMarathonApp(
    appId: AppId
  )(
    implicit session: RequestSession
  ): Future[MarathonApp] = {
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

private case class InstallSuccess(body: InstallResponse)
  extends ExpectedResult(Status.Ok, Some(body.appId))

private case class InstallFailure(
  override val status: Status,
  body: ErrorResponse,
  override val appId: Option[AppId] = None
) extends ExpectedResult(status, appId)

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
    val decoded = new String(Base64.getDecoder.decode(encoded), StandardCharsets.UTF_8)
    val Right(parsed) = parse(decoded)
    parsed
  }

}
