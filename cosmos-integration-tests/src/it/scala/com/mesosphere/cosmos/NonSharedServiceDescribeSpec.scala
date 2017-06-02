package com.mesosphere.cosmos

import _root_.io.circe.Json
import org.scalatest.FeatureSpec
import org.scalatest.GivenWhenThen
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

final class NonSharedServiceDescribeSpec
  extends FeatureSpec
    with GivenWhenThen
    with Matchers
    with TableDrivenPropertyChecks {

  scenario("The user cannot retrieve the options used to run a service") {
    ServiceDescribeSpec.serviceDescribeTest { (content, _, _, _) =>
      Then("the user should not be able to observe the options used to run that service")
      val actualResolvedOptions = content.hcursor.get[Json]("resolvedOptions")
      assert(actualResolvedOptions.isLeft)
    }
  }
  scenario("The user cannot retrieve the options they provided to run a service") {
    ServiceDescribeSpec.serviceDescribeTest { (content, _, _, _) =>
      Then("the user should not be able to observe the options they provided to run a service")
      val actualUserProvidedOptions = content.hcursor.get[Json]("userProvidedOptions")
      assert(actualUserProvidedOptions.isLeft)
    }
  }

}
