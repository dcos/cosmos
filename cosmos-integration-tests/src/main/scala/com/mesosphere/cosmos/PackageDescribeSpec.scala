package com.mesosphere.cosmos

import _root_.io.circe.Json
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.circe.Decoders.parse
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.error.ResultOps
import com.mesosphere.universe
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
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

    "when requesting a v5 response" - {
      "can successfully describe helloworld with a custom manager" - {

        val response = describeRequest(
          rpc.v1.model.DescribeRequest(
            "hello-world",
            Some(universe.v2.model.PackageDetailsVersion("stub-universe"))
          )
        )

        response.status shouldBe Status.Ok

        val description = decode[rpc.v3.model.DescribeResponse](
          response.contentString
        ).getOrThrow

        description.`package`.pkgDef.manager.isDefined shouldBe true
        description.`package`.pkgDef.manager.get.packageName shouldBe "cosmos-package"
        ()
      }
    }

    "when requesting a v3 response" - {
      "can successfully describe helloworld" - {
        "without version" in {
          describeHelloworld()
        }
        "with version" in {
          describeHelloworld(Some(universe.v2.model.PackageDetailsVersion("0.4.2")))
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

        val packageInfo = parse(response.contentString).getOrThrow
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

    val description = decode[rpc.v3.model.DescribeResponse](
      response.contentString
    ).getOrThrow
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
    val errorResponse = decode[rpc.v1.model.ErrorResponse](
      response.contentString
    ).getOrThrow
    assertResult(expectedMessage)(errorResponse.message)
  }

  private def testV3PackageDescribe(
    packageDefinition: Json
  ): Assertion = {
    val Right(name) = packageDefinition.hcursor.get[String]("name")
    val Right(version) = packageDefinition.hcursor.get[String]("version").map(
      universe.v2.model.PackageDetailsVersion(_)
    )

    val response = describeRequest(rpc.v1.model.DescribeRequest(name, Some(version)))

    response.status shouldBe Status.Ok withClue response.contentString
    val actualContent = parse(response.contentString).getOrThrow

    actualContent.hcursor.downField("package").focus shouldBe Some(packageDefinition)
  }


  private def describeHelloworld(
    version: Option[universe.v2.model.PackageDetailsVersion] = None
  ): Assertion = {
    val response = describeRequest(
      rpc.v1.model.DescribeRequest("helloworld", version)
    )

    response.status shouldBe Status.Ok

    val packageInfo = parse(response.contentString).getOrThrow

    packageInfo shouldBe HelloWorld042PackageDefinition
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

  val HelloWorld042PackageDefinition = {
    // scalastyle:off line.size.limit
    val pkgJson = """
    {
      "package" : {
        "packagingVersion" : "4.0",
        "name" : "helloworld",
        "version" : "0.4.2",
        "releaseVersion" : 5,
        "maintainer" : "support@mesosphere.io",
        "description" : "Example DCOS application package",
        "tags" : [ "mesosphere", "example", "subcommand" ],
        "website" : "https://github.com/mesosphere/dcos-helloworld",
        "preInstallNotes" : "A sample pre-installation message",
        "postInstallNotes" : "A sample post-installation message",
        "marathon" : {
          "v2AppMustacheTemplate" : "ewogICJpZCI6ICJ7e25hbWV9fSIsCiAgImNwdXMiOiAxLjAsCiAgIm1lbSI6IDUxMiwKICAiaW5zdGFuY2VzIjogMSwKICAiY21kIjogInB5dGhvbjMgLW0gaHR0cC5zZXJ2ZXIge3twb3J0fX0iLAogICJjb250YWluZXIiOiB7CiAgICAidHlwZSI6ICJET0NLRVIiLAogICAgImRvY2tlciI6IHsKICAgICAgImltYWdlIjogInB5dGhvbjozIiwKICAgICAgIm5ldHdvcmsiOiAiSE9TVCIKICAgIH0KICB9Cn0K"
        },
        "config" : {
          "$schema" : "http://json-schema.org/schema#",
          "type" : "object",
          "properties" : {
            "name" : {
              "type" : "string",
              "default" : "helloworld"
            },
            "port" : {
              "type" : "integer",
              "default" : 8080
            }
          },
          "additionalProperties" : false
        },
        "upgradesFrom" : [ "*" ],
        "downgradesTo" : [ "*" ]
      }
    }
    """
    // scalastyle:on line.size.limit

    parse(pkgJson).getOrThrow
  }

}
