package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpErrorResponse
import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.ItUtil
import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.RoundTrips
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.http.ServiceRpcPath
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.twitter.finagle.http.Status
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax._
import java.util.UUID
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

class ServiceUpdateSpec extends FeatureSpec with Matchers {
  private[this] implicit val testContext = TestContext.fromSystemProperties()

  // scalastyle:off multiple.string.literals
  val helloworld = "helloworld"

  feature("The service/update endpoint") {
    scenario(
      "The user must be able to update the configuration" +
        " used to run a service while keeping any non-conflicting stored configuration items"
    ) {
      val name = "hello-world"
      val disk = 42.asJson
      val options = JsonObject.singleton(
        "hello",
        Json.obj("cpus" -> 0.111.asJson, "disk" -> disk)
      )
      RoundTrips.withInstallV1(name, options = Some(options)).runWith { ir =>
        waitForDeployment()
        val cpus = 0.222.asJson
        val newOptions = JsonObject.singleton("hello", Json.obj("cpus" -> cpus))

        val response = serviceUpdate(
          ir.appId,
          None,
          Some(newOptions),
          false
        )

        val hello = response.resolvedOptions("hello").get.hcursor

        val Right(actualCpus) = hello.downField("cpus").as[Json]
        val Right(actualDisk) = hello.downField("disk").as[Json]

        assertResult(cpus)(actualCpus)
        assertResult(disk)(actualDisk)
      }
    }

    scenario("The user must be able to update the options used to run a service") {
      // Install the service
      val name = helloworld
      val version = "0.1.0".detailsVersion
      val appId = AppId(UUID.randomUUID().toString)
      val options = Some(JsonObject.singleton("name", appId.asJson))

      RoundTrips.withInstallV1(name, Some(version), options).runWith { _ =>
        waitForDeployment()
        // Update the service
        val expectedOptions = JsonObject.fromIterable(
          List(
            "port" -> 8888.asJson,
            "name" -> appId.asJson
          )
        )
        val response = serviceUpdate(
          appId,
          None,
          Some(expectedOptions),
          false
        )

        // check that the new options are correct
        response.resolvedOptions shouldBe expectedOptions

        // we should corroborate with describe
        val describeResponse = Requests.describeService(appId)
        describeResponse.resolvedOptions shouldBe Some(expectedOptions)
      }
    }

    scenario(
      "The user must receive an error if attempting to update a service that does not exist"
    ) {
      val appId = AppId("/does-not-exist")
      val options = JsonObject.singleton("port", 8088.asJson)
      val error = intercept[HttpErrorResponse](serviceUpdate(appId, None, Some(options), false))

      error.status shouldBe Status.BadRequest
      error.errorResponse  shouldBe rpc.v1.model.ErrorResponse(
        "MarathonAppNotFound",
        "Unable to locate service with marathon appId: '/does-not-exist'",
        Some(JsonObject.singleton("appId", "/does-not-exist".asJson))
      )
    }

    scenario(
      "The user must receive an error if attempting an update with invalid options"
    ) {
      val name = helloworld
      val version = "0.1.0".detailsVersion
      val appId = AppId(UUID.randomUUID().toString)
      val options = Some(JsonObject.singleton("name", appId.asJson))

      RoundTrips.withInstallV1(name, Some(version), options).runWith { _ =>
        waitForDeployment()
        val badOptions = JsonObject.fromIterable(
          List("port" -> "8888".asJson, "name" -> appId.asJson)
        )
        // port must be int
        val error = intercept[HttpErrorResponse](
          serviceUpdate(appId, None, Some(badOptions), false)
        )

        error.status shouldBe Status.BadRequest
        error.errorResponse.`type` shouldBe "JsonSchemaMismatch"
        error.errorResponse.message shouldBe "Options JSON failed validation"
      }
    }
  }

  private def waitForDeployment() = {
    // scalastyle:off magic.number
    ItUtil.waitForDeployment(CosmosIntegrationTestClient.adminRouter)(60)
    // scalastyle:on magic.number
  }

  private def serviceUpdate(
    appId: AppId,
    packageVersion: Option[universe.v3.model.Version],
    options: Option[JsonObject],
    replace: Boolean
  ): rpc.v1.model.ServiceUpdateResponse = {
    Requests.callEndpoint[rpc.v1.model.ServiceUpdateResponse](
      HttpRequest.post(
        ServiceRpcPath("update"),
        rpc.v1.model.ServiceUpdateRequest(
          appId,
          packageVersion,
          options,
          replace
        ),
        MediaTypes.ServiceUpdateRequest,
        MediaTypes.ServiceUpdateResponse
      )
    )
  }

  // scalastyle:on multiple.string.literals
}
