package com.mesosphere.universe.v3.model

import com.mesosphere.Generators.nonNegNum
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class PackageDefinitionSpec extends FreeSpec with PropertyChecks {

  "PackageDefinition$.ReleaseVersion" - {

    "ReleaseVersion$.apply should" - {

      "succeed on non-negative numbers" in {
        forAll (nonNegNum[Long]) { n =>
          whenever (n >= 0) {
            assert(ReleaseVersion(n).isReturn)
          }
        }
      }

      "fail on negative numbers" in {
        forAll (Gen.negNum[Long]) { n =>
          whenever (n < 0) {
            assert(ReleaseVersion(n).isThrow)
          }
        }
      }

    }

    "ReleaseVersion.value" in {
      forAll (nonNegNum[Long]) { n =>
        assertResult(n)(ReleaseVersion(n).get.value)
      }
    }

    "ReleaseVersion$.ordering orders by value" in {
      forAll (nonNegNum[Long], nonNegNum[Long]) { (a, b) =>
        whenever (a >= 0 && b >= 0) {
          val aVersion = ReleaseVersion(a).get
          val bVersion = ReleaseVersion(b).get

          assertResult(Ordering[Long].compare(a, b)) {
            Ordering[ReleaseVersion].compare(aVersion, bVersion)
          }
        }
      }
    }

  }

}
