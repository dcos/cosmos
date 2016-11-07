package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.jawn._
import _root_.io.circe.syntax._
import cats.data.Xor
import cats.data.Xor.Right
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.DescribeRequest
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.ListVersionsRequest
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.CosmosRequest
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppContainer
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppContainerDocker
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v2.model._
import com.twitter.finagle.http._
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackageDescribeSpec extends FreeSpec with TableDrivenPropertyChecks {

  import PackageDescribeSpec._

  "The package describe endpoint" - {
    "returns an error when package w/ version not found" in {
      forAll (PackageDummyVersionsTable) { (packageName, packageVersion) =>
        describeAndAssertError(
          packageName = packageName,
          status = Status.BadRequest,
          expectedMessage = s"Version [$packageVersion] of package [$packageName] not found",
          version = Some(packageVersion)
        )
      }
    }

    "can successfully describe helloworld" in {
      describeHelloworld()
      describeHelloworld(Some(PackageDetailsVersion("0.1.0")))
    }

    "can successfully describe all versions from Universe" in {
      forAll (PackageVersionsTable) { (packageName, versions) =>
        describeVersionsAndAssert(
          packageName=packageName,
          status=Status.Ok,
          content=versions.asJson
        )
      }
    }

    "should return an error if Describe is called on a v3 package without a marathon template" in {
      describeAndAssertError(
        "enterprise-security-cli",
        Status.BadRequest,
        "Package: [enterprise-security-cli] version: [0.8.0] does not have a Marathon template defined and can not be rendered"
      )
    }
  }

  val DescribeEndpoint = "package/describe"
  val ListVersionsEndpoint = "package/list-versions"

  private[cosmos] def describeAndAssertError(
    packageName: String,
    status: Status,
    expectedMessage: String,
    version: Option[PackageDetailsVersion] = None
  ): Unit = {
    val response = describeRequest(DescribeRequest(packageName, version))
    assertResult(status)(response.status)
    val Right(errorResponse) = decode[ErrorResponse](response.contentString)
    assertResult(expectedMessage)(errorResponse.message)
  }

  private[cosmos] def describeVersionsAndAssert(
    packageName: String,
    status: Status,
    content: Json
  ): Unit = {
    val response = listVersionsRequest(ListVersionsRequest(packageName, includePackageVersions = true))
    assertResult(status)(response.status)
    assertResult(Xor.Right(content))(parse(response.contentString))
  }

  private[cosmos] def describeHelloworld(version: Option[PackageDetailsVersion] = None) = {
    val response = describeRequest(DescribeRequest("helloworld", version))
    assertResult(Status.Ok)(response.status)
    val Right(packageInfo) = parse(response.contentString)

    val Right(packageJson) = packageInfo.cursor.get[PackageDetails]("package")
    assertResult(HelloworldPackageDef)(packageJson)

    val Right(configJson) = packageInfo.cursor.get[Json]("config")
    assertResult(HelloworldConfigDef)(configJson)

    val Right(commandJson) = packageInfo.cursor.get[Command]("command")
    assertResult(HelloworldCommandDef)(commandJson)
  }

  private[this] def describeRequest(
    describeRequest: DescribeRequest
  ): Response = {
    val request = CosmosRequest.post(
      DescribeEndpoint,
      describeRequest,
      MediaTypes.DescribeRequest,
      MediaTypes.V1DescribeResponse
    )
    CosmosClient.submit(request)
  }

  private[this] def listVersionsRequest(
    listVersionsRequest: ListVersionsRequest
  ): Response = {
    val request = CosmosRequest.post(
      ListVersionsEndpoint,
      listVersionsRequest,
      MediaTypes.ListVersionsRequest,
      MediaTypes.ListVersionsResponse
    )
    CosmosClient.submit(request)
  }
}

private object PackageDescribeSpec extends TableDrivenPropertyChecks {

  private val PackageDummyVersionsTable = Table(
    ("package name", "version"),
    ("helloworld", PackageDetailsVersion("a.b.c")),
    ("cassandra", PackageDetailsVersion("foobar"))
  )

  private val PackageVersionsTable = Table(
    ("package name", "versions"),
    ("helloworld", Map("results" -> Map("0.1.0" -> "0")))
  )

  val HelloworldPackageDef = PackageDetails(
    packagingVersion = PackagingVersion("2.0"),
    name = "helloworld",
    version = PackageDetailsVersion("0.1.0"),
    website = Some("https://github.com/mesosphere/dcos-helloworld"),
    maintainer = "support@mesosphere.io",
    description = "Example DCOS application package",
    preInstallNotes = Some("A sample pre-installation message"),
    postInstallNotes = Some("A sample post-installation message"),
    tags = List("mesosphere", "example", "subcommand"),
    selected = Some(false),
    framework = None
  )

  val HelloworldConfigDef = Json.obj(
    "$schema" -> "http://json-schema.org/schema#".asJson,
    "type" -> "object".asJson,
    "properties" -> Json.obj(
      "port" -> Json.obj(
        "type" -> "integer".asJson,
        "default" -> 8080.asJson
      )
    ),
    "additionalProperties" -> Json.False
  )

  val HelloworldCommandDef = Command(
    List("dcos<1.0", "git+https://github.com/mesosphere/dcos-helloworld.git#dcos-helloworld=0.1.0")
  )

  val mem = 512.0
  val HelloworldMustacheDef = MarathonApp(
    id = AppId("helloworld"),
    cpus = 1.0,
    mem = mem,
    instances = 1,
    cmd = Some("python3 -m http.server {{port}}"),
    container = Some(MarathonAppContainer("DOCKER", Some(MarathonAppContainerDocker("python:3", Some("HOST"))))),
    labels = Map.empty,
    uris = List.empty
  )
}
