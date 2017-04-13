package com.mesosphere.cosmos

import org.scalatest.FreeSpec

class BuildPropertiesSpec extends FreeSpec {

  "BuildProperties for" - {

    "empty should" - {
      val empty = new BuildProperties("/com/mesosphere/cosmos/empty-build.properties")

      "property load" in {
        assertResult("unknown-version")(empty.cosmosVersion)
      }

    }

    "build.properties should" - {
      val props = new BuildProperties("/com/mesosphere/cosmos/test-build.properties")

      "property load" in {
        assertResult("test")(props.cosmosVersion)
      }

    }

    "non-existent resource should" - {

      "throw IllegalStateException" in {
        try {
          val _ = new BuildProperties("/does/not/exist")
        } catch {
          case ies: IllegalStateException => // expected
        }
      }

    }

    "build.properties should load" in {
      val regex = "\\d+\\.\\d+\\.\\d+(?:-SNAPSHOT)?"
      val defaults = BuildProperties()
      val v = defaults.cosmosVersion
      if (v != s"$${version}") {
        assert(v.matches(regex), s"expected 'cosmos.version' to be either template value '$${version}' or match regex: '$regex'")
      }
    }

  }

}
