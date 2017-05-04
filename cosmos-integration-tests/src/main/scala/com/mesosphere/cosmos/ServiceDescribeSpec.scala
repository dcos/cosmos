package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.jawn._
import _root_.io.circe.syntax._
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import org.scalatest.AppendedClues._
import org.scalatest.Assertion
import org.scalatest.FeatureSpec
import org.scalatest.GivenWhenThen
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.TableFor3

class ServiceDescribeSpec
  extends FeatureSpec
    with GivenWhenThen
    with Matchers
    with TableDrivenPropertyChecks {

  val path: String = "service/describe"
  val contentType: String = "application/vnd.dcos.service.describe-request+json;charset=utf-8;version=v1"
  val accept: String = "application/vnd.dcos.service.describe-response+json;charset=utf-8;version=v1"

  feature("The service/describe endpoint") {
    scenario("The user would like to know the upgrades available to a service") {
      serviceDescribeTest { (content, _, expectedUpgrades, _) =>
        Then("the user should be able to observe the upgrades available to that service")
        val Right(actualUpgrades) = content.hcursor.get[Json]("upgradesTo")
        expectedUpgrades.asJson shouldBe actualUpgrades
      }
    }
    scenario("The user would like to know the downgrades available to a service") {
      serviceDescribeTest { (content, packageDefinition, _, _) =>
        Then("the user should be able to observe the downgrades available to that service")
        val expectedDowngrades = packageDefinition.downgradesTo
        val Right(actualDowngrades) = content.hcursor.get[Json]("downgradesTo")
        expectedDowngrades.asJson shouldBe actualDowngrades
      }
    }
    scenario("The user would like to know the options used to run a service") {
      serviceDescribeTest { (content, _, _, expectedOptions) =>
        Then("the user should be able to observe the options used to run that service")
        val Right(actualOptions) = content.hcursor.get[Json]("resolvedOptions")
        actualOptions shouldBe expectedOptions
      }
    }
    scenario("The user would like to know the package definition used to run a service") {
      serviceDescribeTest { (content, packageDefinition, expectedUpgrades, _) =>
        Then("the user should be able to observe the package definition used to run that service")
        val expectedDefinition = packageDefinition.asJson
        val Right(actualDefinition) = content.hcursor.get[Json]("package")
        actualDefinition shouldBe expectedDefinition
      }
    }
  }

  def serviceDescribeTest(
    testCode: (Json, universe.v4.model.PackageDefinition, List[String], Json) => Assertion
  ): Unit = {
    forAll(helloWorldPackageDefinitions) { (packageDefinition, expectedUpgrades, resolvedOptions) =>
      Given("a running service and its appId")
      val name = packageDefinition.name
      val version = packageDefinition.version.toString
      val Right(install) = ItUtil.packageInstall(name, Some(version))
      val appId = install.appId

      When("the user makes a request to the service/describe endpoint")
      val response = serviceDescribe(appId.toString)
      response.status shouldBe Status.Ok withClue response.contentString
      response.contentType shouldBe Some(accept)
      val Right(content) = parse(response.contentString)

      // the actual test
      testCode(content, packageDefinition, expectedUpgrades, resolvedOptions)

      // clean up
      ItUtil.packageUninstall(name, appId, all = true)
    }
  }

  private def serviceDescribe(appId: String): Response = {
    val body = Json.obj(
      "appId" -> appId.asJson
    )
    CosmosClient.submit(
      HttpRequest.post(
        path = path,
        body = body.noSpaces,
        contentType = Some(contentType),
        accept = Some(accept)
      )
    )
  }

  private val helloWorldPackageDefinitions
  : TableFor3[universe.v4.model.PackageDefinition, List[String], Json] = {
    Table(
      ("Package Definition", "Upgrades To", "Resolved Options")
    )
  }

}

