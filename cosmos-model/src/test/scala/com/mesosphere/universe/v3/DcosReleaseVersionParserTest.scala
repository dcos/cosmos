package com.mesosphere.universe.v3

import com.mesosphere.universe.v3.DcosReleaseVersion.{Suffix, Version}
import com.twitter.util.{Return, Throw, Try}
import org.scalatest.FreeSpec

class DcosReleaseVersionParserTest extends FreeSpec {
  private[this] val regex = "^(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*))*(?:-[A-Za-z0-9]+)?$"

  "DcosReleaseVersionParser should" - {
    "succeed for" - {
      "1" in {
        val Return(parse) = DcosReleaseVersionParser.parse("1")
        assertResult(DcosReleaseVersion(Version(1)))(parse)
      }

      "10.200.3000.40000.500000-oneMillion" in {
        val Return(parse) = DcosReleaseVersionParser.parse("10.200.3000.40000.500000-oneMillion")
        val expected = DcosReleaseVersion(
          Version(10),
          List(Version(200), Version(3000), Version(40000), Version(500000)),
          Some(Suffix("oneMillion"))
        )
        assertResult(expected)(parse)
      }

      "1-ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" in {
        val Return(parse) = DcosReleaseVersionParser.parse("1-ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        val expected = DcosReleaseVersion(
          Version(1),
          List.empty,
          Some(Suffix("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"))
        )
        assertResult(expected)(parse)
      }
    }

    "fail for" - {

      "empty string" in {
        assertAssertionError(s"assertion failed: Value must not be empty") {
          DcosReleaseVersionParser.parse("")
        }
      }

      "only spaces" in {
        assertAssertionError(s"assertion failed: Value must not be empty") {
          DcosReleaseVersionParser.parse("    ")
        }
      }

      "01" in {
        assertAssertionError(s"assertion failed: Value '01' does not conform to expected format $regex") {
          DcosReleaseVersionParser.parse("01")
        }
      }

      "1--" in {
        assertAssertionError(s"assertion failed: Value '1--' does not conform to expected format $regex") {
          DcosReleaseVersionParser.parse("1--")
        }
      }

      "2.01" in {
        assertAssertionError(s"assertion failed: Value '2.01' does not conform to expected format $regex") {
          DcosReleaseVersionParser.parse("2.01")
        }
      }

    }
  }

  private[this] def assertAssertionError[T](expectedMessage: String)(f: => Try[T]): Unit = {
    f match {
      case Throw(ae: AssertionError) =>
        assertResult(expectedMessage)(ae.getMessage)
      case Throw(t) =>
        fail("unexpected throwable", t)
      case Return(r) =>
        fail(s"Return($r) when expected an assertion error")
    }
  }

}
