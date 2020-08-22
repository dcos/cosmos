package com.mesosphere.universe.v3.model

import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import org.scalatest.Assertion
import org.scalatest.FreeSpec

class DcosReleaseVersionParserSpec extends FreeSpec {
  private[this] val regex = DcosReleaseVersionParser.fullRegex.toString

  "DcosReleaseVersionParser should" - {
    "succeed for" - {
      "1" in {
        val Return(parse) = DcosReleaseVersionParser.parse("1")
        assertResult(DcosReleaseVersion(DcosReleaseVersion.Version(1)))(parse)
      }

      // scalastyle:off magic.number
      "10.200.3000.40000.500000-oneMillion" in {
        val Return(parse) = DcosReleaseVersionParser.parse("10.200.3000.40000.500000-oneMillion")
        val expected = DcosReleaseVersion(
          DcosReleaseVersion.Version(10),
          List(DcosReleaseVersion.Version(200), DcosReleaseVersion.Version(3000), DcosReleaseVersion.Version(40000), DcosReleaseVersion.Version(500000)),
          Some(DcosReleaseVersion.Suffix("oneMillion"))
        )
        assertResult(expected)(parse)
      }
      // scalastyle:off magic.number

      "1-ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" in {
        val Return(parse) = DcosReleaseVersionParser.parse("1-ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        val expected = DcosReleaseVersion(
          DcosReleaseVersion.Version(1),
          List.empty,
          Some(DcosReleaseVersion.Suffix("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"))
        )
        assertResult(expected)(parse)
      }

      "1.2.3-alpha.7" in {
        val Return(parse) = DcosReleaseVersionParser.parse("1.2.3-alpha.7")
        val expected = DcosReleaseVersion(
          DcosReleaseVersion.Version(1),
          List(DcosReleaseVersion.Version(2), DcosReleaseVersion.Version(3)),
          Some(DcosReleaseVersion.Suffix("alpha.7"))
        )
        assertResult(expected)(parse)
      }

      "1.2.3-abc123-aA.123-pP.99-ts" in {
        val Return(parse) = DcosReleaseVersionParser.parse("1.2.3-abc123-aA.123-pP.99-ts")
        val expected = DcosReleaseVersion(
          DcosReleaseVersion.Version(1),
          List(DcosReleaseVersion.Version(2), DcosReleaseVersion.Version(3)),
          Some(DcosReleaseVersion.Suffix("abc123-aA.123-pP.99-ts"))
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

      "1.2.3-" in {
        assertAssertionError(s"assertion failed: Value '1.2.3-' does not conform to expected format $regex") {
          DcosReleaseVersionParser.parse("1.2.3-")
        }
      }

      "2.01" in {
        assertAssertionError(s"assertion failed: Value '2.01' does not conform to expected format $regex") {
          DcosReleaseVersionParser.parse("2.01")
        }
      }

    }
  }

  private[this] def assertAssertionError[T](expectedMessage: String)(f: => Try[T]): Assertion = {
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
