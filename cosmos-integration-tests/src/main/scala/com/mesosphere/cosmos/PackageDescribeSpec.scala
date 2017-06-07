package com.mesosphere.cosmos

import _root_.io.circe.Json
import cats.syntax.either._
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.circe.Decoders.parse
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import java.util.Base64
import org.scalatest.AppendedClues._
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

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

    "when requesting a v3 response" - {
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
          testV3PackageDescribe(packageDefinition)
        }
      }

      "should succeed to describe helloworld v4" in {
        val response = CosmosClient.submit(
          CosmosRequests.packageDescribeV3(
            rpc.v1.model.DescribeRequest(
              "helloworld",
              Some(universe.v2.model.PackageDetailsVersion("0.4.1"))
            )
          )
        )

        response.status shouldBe Status.Ok

        val packageInfo = parse(response.contentString)
        packageInfo.hcursor.downField("package").get[String]("name") shouldBe Right("helloworld")
        packageInfo.hcursor.downField("package").get[String]("version") shouldBe Right("0.4.1")
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
  }

  private def packageDescribeLatestAndAssert(
    packageName: String,
    expectedVersion: universe.v3.model.Version
  ): Assertion = {
    val response = describeRequest(rpc.v1.model.DescribeRequest(packageName, None))

    response.status shouldBe Status.Ok

    val description = decode[rpc.v3.model.DescribeResponse](response.contentString)
    description.`package`.version shouldBe expectedVersion
  }

  private def describeAndAssertError(
    packageName: String,
    status: Status,
    expectedMessage: String,
    version: Option[universe.v2.model.PackageDetailsVersion]
  ): Assertion = {
    val response = describeRequest(
      rpc.v1.model.DescribeRequest(packageName, version)
    )
    assertResult(status)(response.status)
    val errorResponse = decode[rpc.v1.model.ErrorResponse](response.contentString)
    assertResult(expectedMessage)(errorResponse.message)
  }

  private def testV3PackageDescribe(
    packageDefinition: Json
  ): Assertion = {
    val Right(name) = packageDefinition.cursor.get[String]("name")
    val Right(version) = packageDefinition.cursor.get[String]("version").map(
      universe.v2.model.PackageDetailsVersion
    )

    val response = describeRequest(rpc.v1.model.DescribeRequest(name, Some(version)))

    response.status shouldBe Status.Ok withClue response.contentString
    val actualContent = parse(response.contentString)

    actualContent.hcursor.downField("package").focus shouldBe Some(packageDefinition)
  }


  private def describeHelloworld(
    version: Option[universe.v2.model.PackageDetailsVersion] = None
  ): Assertion = {
    val response = describeRequest(
      rpc.v1.model.DescribeRequest("helloworld", version)
    )

    response.status shouldBe Status.Ok

    val packageInfo = parse(response.contentString)

    packageInfo shouldBe HelloWorld010PackageDefinition
  }

  private def describeRequest(
    describeRequest: rpc.v1.model.DescribeRequest
  ): Response = {
    CosmosClient.submit(CosmosRequests.packageDescribeV3(describeRequest))
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
    ("arangodb", universe.v3.model.Version("0.3.0")),
    ("avi", universe.v3.model.Version("16.2")),
    ("cassandra", universe.v3.model.Version("1.0.6-2.2.5")),
    ("confluent", universe.v3.model.Version("1.0.3-3.0.0")),
    ("datadog", universe.v3.model.Version("5.4.3")),
    ("hdfs", universe.v3.model.Version("2.5.2-0.1.9")),
    ("jenkins", universe.v3.model.Version("0.2.3")),
    ("kafka", universe.v3.model.Version("1.1.2-0.10.0.0"))
  )

  val HelloWorld010PackageDefinition = {
    // scalastyle:off line.size.limit
    val pkgJson = """
    {
      "package" : {
        "packagingVersion" : "2.0",
        "name" : "helloworld",
        "version" : "0.1.0",
        "releaseVersion" : 0,
        "maintainer" : "support@mesosphere.io",
        "description" : "Example DCOS application package",
        "marathon" : {
          "v2AppMustacheTemplate" : "ewogICJpZCI6ICJoZWxsb3dvcmxkIiwKICAiY3B1cyI6IDEuMCwKICAibWVtIjogNTEyLAogICJpbnN0YW5jZXMiOiAxLAogICJjbWQiOiAicHl0aG9uMyAtbSBodHRwLnNlcnZlciB7e3BvcnR9fSIsCiAgImNvbnRhaW5lciI6IHsKICAgICJ0eXBlIjogIkRPQ0tFUiIsCiAgICAiZG9ja2VyIjogewogICAgICAiaW1hZ2UiOiAicHl0aG9uOjMiLAogICAgICAibmV0d29yayI6ICJIT1NUIgogICAgfQogIH0KfQo="
        },
        "tags" : [
        "mesosphere",
        "example",
        "subcommand"
        ],
        "website" : "https://github.com/mesosphere/dcos-helloworld",
        "preInstallNotes" : "A sample pre-installation message",
        "postInstallNotes" : "A sample post-installation message",
        "config" : {
          "$schema" : "http://json-schema.org/schema#",
          "type" : "object",
          "properties" : {
            "port" : {
              "type" : "integer",
              "default" : 8080
            }
          },
          "additionalProperties" : false
        },
        "command" : {
          "pip" : [
          "dcos<1.0",
          "git+https://github.com/mesosphere/dcos-helloworld.git#dcos-helloworld=0.1.0"
          ]
        }
      }
    }
    """
    // scalastyle:on line.size.limit

    parse(pkgJson)
  }

}
