package com.mesosphere.universe.v3.model

import com.mesosphere.Generators.Implicits.arbVersion
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class VersionSpecificationSpec extends FreeSpec with PropertyChecks {

  "The matches() method" - {

    "always returns true on AnyVersion" in {
      forAll { (version: Version) => assert(AnyVersion.matches(version)) }
    }

    "returns true on ExactVersion when the versions are equal" in {
      forAll { (version: Version) => assert(ExactVersion(version).matches(version)) }
    }

    "returns false on ExactVersion when the versions are unequal" in {
      forAll { (v1: Version, v2: Version) =>
        whenever (v1 != v2) {
          assert(!ExactVersion(v1).matches(v2))
        }
      }
    }

  }

}
