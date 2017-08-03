package com.mesosphere.cosmos

import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.http.TestContext
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

final class NonSharedServiceDescribeSpec extends FeatureSpec with Matchers {
  private[this] implicit val testContext = TestContext.fromSystemProperties()

  feature("The service/describe endpoint") {
    scenario("The user cannot retrieve the options used to run a service") {
      RoundTrips.withInstallV1("helloworld", Some("0.1.0".detailsVersion)).runWith { ir =>
        Requests.describeService(ir.appId).resolvedOptions.shouldBe(None)
      }
    }
    scenario("The user cannot retrieve the options they provided to run a service") {
      RoundTrips.withInstallV1("helloworld", Some("0.1.0".detailsVersion)).runWith { ir =>
        Requests.describeService(ir.appId).userProvidedOptions.shouldBe(None)
      }
    }
  }
}
