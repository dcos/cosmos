package com.mesosphere.cosmos

import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.error.MarathonAppNotFound
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

final class ServiceDescribeSpec extends FeatureSpec with Matchers {
  private[this] implicit val testContext = TestContext.fromSystemProperties()

  feature("The service/describe endpoint") {
    scenario("The user would like to know the upgrades available to a service") {
      RoundTrips.withInstallV1("helloworld", Some("0.1.0".detailsVersion)).runWith { ir =>
        Requests.describeService(ir.appId).upgradesTo.shouldBe(
          List("0.4.2").map(_.version))
      }
    }
    scenario("The user would like to know the downgrades available to a service") {
      RoundTrips.withInstallV1("helloworld", Some("0.4.2".detailsVersion)).runWith { ir =>
        Requests.describeService(ir.appId).downgradesTo.shouldBe(
          List("0.4.2", "0.4.1", "0.4.0", "0.1.0").map(_.version))
      }
    }
    scenario("The user would like to know the package definition used to run a service") {
      RoundTrips.withInstallV1("helloworld", Some("0.1.0".detailsVersion)).runWith { ir =>
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
  }
}
