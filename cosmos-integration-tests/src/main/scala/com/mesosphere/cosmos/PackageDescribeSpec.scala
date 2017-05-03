package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.jawn.parse
import _root_.io.circe.syntax._
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe
import com.mesosphere.universe.v2.circe.Decoders._
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import java.util.Base64
import org.scalatest.AppendedClues._
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.util.Right
import cats.syntax.either._

final class PackageDescribeSpec
  extends FreeSpec with TableDrivenPropertyChecks with Matchers {

  import PackageDescribeSpec._

  "The package describe endpoint" - {
    "returns an error when package w/ version not found" in {
      forAll(PackageDummyVersionsTable) { (packageName, packageVersion) =>
        describeAndAssertError(
          packageName = packageName,
          status = Status.BadRequest,
          expectedMessage = s"Version [$packageVersion] of package [$packageName] not found",
          version = Some(packageVersion)
        )
      }
    }

    "always returns the latest package" in {
      forAll(LatestPackageVersionsTable) { (packageName, expectedVersion) =>
        packageDescribeLatestAndAssert(
          packageName,
          expectedVersion
        )
      }
    }

    "when requesting a v1 response" - {
      "can successfully describe helloworld" - {
        "without version" in {
          describeHelloworld()
        }
        "with version" in {
          describeHelloworld(Some(universe.v2.model.PackageDetailsVersion("0.1.0")))
        }
      }

      "should return correct Json" in {
        forAll(ItObjects.helloWorldPackageDefinitions) { (packageDefinition, _) =>
          testV1PackageDescribe(packageDefinition)
        }
      }
    }

    "when requesting a v2 response" - {
      "fails to describe helloworld v4 w/ updates when requesting v2 describe response" in {
        val response = CosmosClient.submit(
          CosmosRequests.packageDescribeV2(
            rpc.v1.model.DescribeRequest(
              "helloworld",
              Some(universe.v2.model.PackageDetailsVersion("0.4.1")))
          )
        )
        assertResult(Status.BadRequest)(response.status)
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

  private def packageDescribeLatestAndAssert(
    packageName: String,
    expectedVersion: universe.v2.model.PackageDetailsVersion
  ): Assertion = {
    val response = describeRequest(rpc.v1.model.DescribeRequest(packageName, None))

    response.status shouldBe Status.Ok

    val description = decode[rpc.v1.model.DescribeResponse](response.contentString)
    description.`package`.version shouldBe expectedVersion
  }

  private def describeAndAssertError(
    packageName: String,
    status: Status,
    expectedMessage: String,
    version: Option[universe.v2.model.PackageDetailsVersion] = None
  ): Assertion = {
    val response = describeRequest(
      rpc.v1.model.DescribeRequest(packageName, version)
    )
    assertResult(status)(response.status)
    val errorResponse = decode[rpc.v1.model.ErrorResponse](response.contentString)
    assertResult(expectedMessage)(errorResponse.message)
  }

  private def testV1PackageDescribe(
    packageDefinition: Json
  ): Assertion = {
    val Right(name) =
      packageDefinition.cursor.get[String]("name")
    val Right(version) =
      packageDefinition.cursor.get[String]("version")
        .map(universe.v2.model.PackageDetailsVersion)

    val response = describeRequest(rpc.v1.model.DescribeRequest(name, Some(version)))

    response.status shouldBe Status.Ok withClue response.contentString

    val Right(encodedMustache) =
      packageDefinition.hcursor.downField("marathon").get[String]("v2AppMustacheTemplate")

    val expectedMarathonMustache =
      new String(Base64.getDecoder.decode(encodedMustache))

    val expectedCommand =
      packageDefinition.hcursor.get[Json]("command").toOption

    val expectedConfig =
      packageDefinition.hcursor.get[Json]("config").toOption

    val expectedResource =
      packageDefinition.hcursor.get[Json]("resource").toOption

    val expectedContent = ItObjects.dropNullKeys(
      Json.obj(
        "package" -> ItObjects.helloWorldPackageDetails(packageDefinition),
        "marathonMustache" -> expectedMarathonMustache.asJson,
        "command" -> expectedCommand.asJson,
        "config" -> expectedConfig.asJson,
        "resource" -> expectedResource.asJson
      )
    )

    val Right(actualContent) = parse(response.contentString)

    actualContent shouldBe expectedContent
  }


  private def describeHelloworld(
    version: Option[universe.v2.model.PackageDetailsVersion] = None
  ): Assertion = {
    val response = describeRequest(
      rpc.v1.model.DescribeRequest("helloworld", version)
    )
    assertResult(Status.Ok)(response.status)
    val Right(packageInfo) = parse(response.contentString)

    val Right(packageJson) =
      packageInfo.cursor.get[universe.v2.model.PackageDetails]("package")
    assertResult(HelloworldPackageDef)(packageJson)

    val Right(configJson) = packageInfo.cursor.get[Json]("config")
    assertResult(HelloworldConfigDef)(configJson)

    val Right(commandJson) = packageInfo.cursor.get[universe.v2.model.Command]("command")
    assertResult(HelloworldCommandDef)(commandJson)
  }

  private def describeRequest(
    describeRequest: rpc.v1.model.DescribeRequest
  ): Response = {
    CosmosClient.submit(CosmosRequests.packageDescribeV1(describeRequest))
  }

}

private object PackageDescribeSpec extends TableDrivenPropertyChecks {

  val PackageDummyVersionsTable = Table(
    ("package name", "version"),
    ("helloworld", universe.v2.model.PackageDetailsVersion("a.b.c")),
    ("cassandra", universe.v2.model.PackageDetailsVersion("foobar"))
  )

  val LatestPackageVersionsTable = Table(
    ("package name", "expected version"),
    ("arangodb", universe.v2.model.PackageDetailsVersion("0.3.0")),
    ("avi", universe.v2.model.PackageDetailsVersion("16.2")),
    ("cassandra", universe.v2.model.PackageDetailsVersion("1.0.6-2.2.5")),
    ("confluent", universe.v2.model.PackageDetailsVersion("1.0.3-3.0.0")),
    ("datadog", universe.v2.model.PackageDetailsVersion("5.4.3")),
    ("hdfs", universe.v2.model.PackageDetailsVersion("2.5.2-0.1.9")),
    ("jenkins", universe.v2.model.PackageDetailsVersion("0.2.3")),
    ("kafka", universe.v2.model.PackageDetailsVersion("1.1.2-0.10.0.0"))
  )

  val HelloworldPackageDef = universe.v2.model.PackageDetails(
    packagingVersion = universe.v2.model.PackagingVersion("2.0"),
    name = "helloworld",
    version = universe.v2.model.PackageDetailsVersion("0.1.0"),
    website = Some("https://github.com/mesosphere/dcos-helloworld"),
    maintainer = "support@mesosphere.io",
    description = "Example DCOS application package",
    preInstallNotes = Some("A sample pre-installation message"),
    postInstallNotes = Some("A sample post-installation message"),
    tags = List("mesosphere", "example", "subcommand"),
    selected = Some(false),
    framework = Some(false)
  )

  val HelloworldConfigDef: Json = Json.obj(
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

  val HelloworldCommandDef = universe.v2.model.Command(
    List("dcos<1.0", "git+https://github.com/mesosphere/dcos-helloworld.git#dcos-helloworld=0.1.0")
  )
}
