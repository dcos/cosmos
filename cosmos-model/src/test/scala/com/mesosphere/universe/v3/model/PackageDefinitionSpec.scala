package com.mesosphere.universe.v3.model

import org.scalacheck.Gen
import org.scalacheck.Gen.Choose
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class PackageDefinitionSpec extends FreeSpec with PropertyChecks {

  import PackageDefinitionSpec._

  "PackageDefinition$.ReleaseVersion" - {

    "ReleaseVersion$.apply should" - {

      "succeed on non-negative numbers" in {
        forAll (nonNegNum) { n =>
          whenever (n >= 0) {
            assert(PackageDefinition.ReleaseVersion(n).isReturn)
          }
        }
      }

      "fail on negative numbers" in {
        forAll (Gen.negNum[Long]) { n =>
          whenever (n < 0) {
            assert(PackageDefinition.ReleaseVersion(n).isThrow)
          }
        }
      }

    }

    "ReleaseVersion.value" in {
      forAll (nonNegNum) { n =>
        assertResult(n)(PackageDefinition.ReleaseVersion(n).get.value)
      }
    }

    "ReleaseVersion$.ordering orders by value" in {
      forAll (nonNegNum, nonNegNum) { (a, b) =>
        whenever (a >= 0 && b >= 0) {
          val aVersion = PackageDefinition.ReleaseVersion(a).get
          val bVersion = PackageDefinition.ReleaseVersion(b).get

          assertResult(Ordering[Long].compare(a, b)) {
            Ordering[PackageDefinition.ReleaseVersion].compare(aVersion, bVersion)
          }
        }
      }
    }

  }

}

object PackageDefinitionSpec {
  val nonNegNum: Gen[Long] = Gen.sized(max => implicitly[Choose[Long]].choose(0, max.toLong))
}
