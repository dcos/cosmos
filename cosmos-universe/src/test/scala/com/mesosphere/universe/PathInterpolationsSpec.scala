package com.mesosphere.universe

import org.scalatest.FreeSpec
import org.scalatest.Matchers

final class PathInterpolationsSpec extends FreeSpec with Matchers {

  "AbsolutePath interpolation" - {

    "is successful for" - {

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

    "fails for" - {

      "empty string" in {
        """ abspath"" """ shouldNot compile
      }

      "relative path with one element" in {
        """ abspath"foo" """ shouldNot compile
      }

      "relative path with two elements" in {
        """ abspath"foo/bar" """ shouldNot compile
      }

      "path with interpolated value" in {
        """ { val bar = "bar"; abspath"/foo/$bar/baz" } """ shouldNot compile
      }

    }

  }

}
