package com.mesosphere.util

import org.scalatest.FreeSpec
import org.scalatest.Matchers

final class PathInterpolationsSpec extends FreeSpec with Matchers {

  "AbsolutePath interpolation" - {

    "is successful for" - {

      "root" in {
        assertResult(AbsolutePath("/").right.get)(abspath"/")
      }

      "single element" in {
        assertResult(AbsolutePath("/foo").right.get)(abspath"/foo")
      }

      "two elements" in {
        assertResult(AbsolutePath("/foo/bar").right.get)(abspath"/foo/bar")
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

      "two leading slashes" in {
        """ abspath"//foo/bar" """ shouldNot compile
      }

      "path with interpolated value" in {
        """ { val bar = "bar"; abspath"/foo/$bar/baz" } """ shouldNot compile
      }

      "non-literal use of interpolator" in {
        """ { val path = "/foo/bar"; StringContext(path).abspath() } """ shouldNot compile
      }

    }

  }

}
