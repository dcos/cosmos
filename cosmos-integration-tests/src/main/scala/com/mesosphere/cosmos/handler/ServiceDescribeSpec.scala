package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpErrorResponse
import com.mesosphere.cosmos.IntegrationBeforeAndAfterAll
import com.mesosphere.cosmos.ItObjects
import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.RoundTrips
import com.mesosphere.cosmos.error.MarathonAppNotFound
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.ServiceDescribeRequest
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

final class ServiceDescribeSpec extends FeatureSpec with Matchers with IntegrationBeforeAndAfterAll{
  private[this] implicit val testContext = TestContext.fromSystemProperties()

  private[this] val name = "helloworld"
  private[this] val port = 9999

  feature("The service/describe endpoint") {
    scenario("The user would like to know the upgrades available to a service") {
      RoundTrips.withInstallV1(name, Some("0.1.0".detailsVersion)).runWith { ir =>
        Requests.describeService(ir.appId).upgradesTo.shouldBe(
          List("0.4.2").map(_.version))
      }
    }
    scenario("The user would like to know the downgrades available to a service") {
      RoundTrips.withInstallV1(name, Some("0.4.2".detailsVersion)).runWith { ir =>
        Requests.describeService(ir.appId).downgradesTo.shouldBe(
          List("0.4.2", "0.4.1", "0.4.0", "0.1.0").map(_.version))
      }
    }
    scenario("The user would like to know the package definition used to run a service") {
      RoundTrips.withInstallV1(name, Some("0.1.0".detailsVersion)).runWith { ir =>
        Requests.describeService(ir.appId).`package`.shouldBe(
          Requests.describePackage(ir.packageName, Some(ir.packageVersion)).`package`)
      }
    }
    scenario("The user should receive an error if the service is not present") {
      val appId = AppId("/does-not-exist-4204242")
      val error = intercept[HttpErrorResponse](Requests.describeService(appId))
      error.status shouldBe Status.BadRequest
      error.errorResponse.shouldBe(MarathonAppNotFound(appId).as[ErrorResponse])
    }
    scenario("The user would like to know the options used to run a service") {
      val options = s"""{ "port": $port, "name": "$name" }""".json.asObject
      RoundTrips.withInstallV1(name, Some("0.1.0".detailsVersion), options).runWith { ir =>
        Requests.describeService(ir.appId).resolvedOptions.shouldBe(options)
      }
    }
    scenario("The user would like to know the options he provided to run a service") {
      val options = s"""{ "port": $port }""".json.asObject
      RoundTrips.withInstallV1(name, Some("0.1.0".detailsVersion), options).runWith { ir =>
        Requests.describeService(ir.appId).userProvidedOptions.shouldBe(options)
      }
    }
    scenario("The user would like to describe a service via a custom manager") {
      // Set to use cassandra 2.0.0-3.0.1 which is the most recent version to allow only a single node to run.
      val cassandraOptions = s"""{ "nodes": { "count": 1 }}""".json.asObject
      val appId = AppId("cassandra")
      Requests.installV2("cassandra", version = Some("2.0.0-3.0.1".detailsVersion), options = cassandraOptions, appId = Some(appId), managerId = Some(ItObjects.customManagerAppName))

      val serviceDescribeRequest = ServiceDescribeRequest(appId, Some(ItObjects.customManagerAppName), None, None)
      val serviceDescribeResponse = submitServiceDescribeRequest(serviceDescribeRequest)
      assertResult(Status.Ok)(serviceDescribeResponse.status)

      Requests.uninstall("cassandra", managerId = Some(ItObjects.customManagerAppName))
      Requests.waitForMarathonAppToDisappear(appId)
    }
  }

  def submitServiceDescribeRequest(
    request: ServiceDescribeRequest
  )(
    implicit testContext: TestContext
  ): Response = {
    CosmosClient.submit(CosmosRequests.serviceDescribe(request))
  }
}
