package com.mesosphere.universe

import org.scalatest.FreeSpec

final class PathInterpolationsSpec extends FreeSpec {

  "AbsolutePath interpolation" - {

    "root" in {
      assertResult("/")(abspath"/".toString)
    }

    "single element" in {
      assertResult("/foo")(abspath"/foo".toString)
    }

    "two elements" in {
      assertResult("/foo/bar")(abspath"/foo/bar".toString)
    }

  }

}
