package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpErrorResponse
import com.mesosphere.cosmos.ItObjects
import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.RoundTrips
import com.mesosphere.cosmos.error.VersionUpgradeNotSupportedInOpen
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.twitter.finagle.http.Status
import io.circe.JsonObject
import io.circe.syntax._
import java.util.UUID
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

class NonSharedServiceUpdateSpec extends FeatureSpec with Matchers {
  private[this] implicit val testContext = TestContext.fromSystemProperties()

  import ServiceUpdateSpec._

  // scalastyle:off multiple.string.literals
  val helloworld = "helloworld"

  feature("The service/update endpoint") {
    scenario("The user must get an error when attempting to upgrade a service") {
      val name = helloworld
      val version = "0.4.0"
      val appId = AppId(UUID.randomUUID().toString)
      val expectedOptions = JsonObject.fromIterable(
        List("port" -> 8888.asJson, "name" -> appId.asJson)
      )
      val expectedPackage = ItObjects.helloWorldPackage4

      val expectedError = cosmosErrorToErrorResponse(
        VersionUpgradeNotSupportedInOpen(
          requested = Some(expectedPackage.version),
          actual = version.version
        )
      )

      RoundTrips.withInstallV1(name, Some(version.detailsVersion), Some(expectedOptions)).runWith { _ =>
        waitForDeployment()
        val error = intercept[HttpErrorResponse] {
          serviceUpdate(appId, Some(expectedPackage.version), None, false)
        }
        error.status shouldBe Status.BadRequest
        error.errorResponse shouldBe expectedError
      }
    }
  }
  // scalastyle:on multiple.string.literals
}
