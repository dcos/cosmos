package com.mesosphere.universe.v3.model

import com.mesosphere.universe.v3.model.DcosReleaseVersion.{Suffix, Version}
import com.twitter.util.{Return, Throw, Try}
import org.scalatest.FreeSpec

import scala.language.implicitConversions

class DcosReleaseVersionSpec extends FreeSpec {

  private[this] implicit def stringToDcosReleaseVersion(s: String): DcosReleaseVersion = {
    DcosReleaseVersionParser.parseUnsafe(s)
  }
  implicit val o = DcosReleaseVersion.dcosReleaseVersionOrdering

  "DcosReleaseVersion" - {
    "Ordering should" - {
      "make operators available" - {
        import DcosReleaseVersion._
        val left = DcosReleaseVersion(Version(1), List(Version(0)))
        val right = DcosReleaseVersion(Version(1), List(Version(0), Version(1)))
        "<=" in {
          assert(left <= right)
        }
        "<" in {
          assert(left < right)
        }
        ">=" in {
          assert(!(left >= right))
        }
        ">" in {
          assert(!(left > right))
        }
        "equiv" in {
          assert(!(left equiv right))
        }
        "max" in {
          assertResult(right)(left max right)
        }
        "min" in {
          assertResult(left)(left min right)
        }
      }
      "pass for" - {
        "1 == 1" in {
          assert(o.equiv("1", "1"))
        }
        "1.0.0 == 1.0.0" in {
          assert(o.equiv("1.0.0", "1.0.0"))
        }
        "1.0.0 <= 1.0.0" in {
          assert(o.lteq("1.0.0", "1.0.0"))
        }

        "1.0.0 >= 1.0.0" in {
          assert(o.gteq("1.0.0", "1.0.0"))
        }

        "1.0.0 >= 1.0" in {
          assert(o.gteq("1.0.0", "1.0"))
        }

        "0.1-beta >= 0.1" in {
          assert(o.gteq("0.1-beta", "0.1"))
        }
        "0.1-beta == 0.1" in {
          assert(o.equiv("0.1-beta", "0.1"))
        }
        "0.1-beta <= 0.1" in {
          assert(o.lteq("0.1-beta", "0.1"))
        }

        "1.7.0 >= 1.7.0-dev" in {
          assert(o.gteq("1.7.0", "1.7.0-dev"))
        }

        "1.7 >= 1.7.0" in {
          assert(o.gteq("1.7", "1.7.0"))
        }

        "3 >= 1.7" in {
          assert(o.gteq("3", "1.7"))
        }

        "15 >= 14" in {
          assert(o.gteq("15", "14"))
        }
        "15 > 14" in {
          assert(o.gt("15", "14"))
        }

        // this test ensures that short circuit evaluation is being done, because the first value is greater than,
        // but all others are less than.
        "10.20.30.40 >= 1.50.60.70" in {
          assert(o.gteq("10.20.30.40", "1.50.60.70"))
        }
      }

      "fail for" - {
        "1.0.0 < 1.0.0" in {
          assert(!o.lt("1.0.0", "1.0.0"))
        }
        "1.0.0 > 1.0" in {
          assert(!o.gt("1.0.0", "1.0"))
        }

        "0.1-beta < 0.1" in {
          assert(!o.lt("0.1-beta", "0.1"))
        }
        "0.1-beta > 0.9" in {
          assert(!o.gt("0.1-beta", "0.9"))
        }

        "0.9 > 1.0" in {
          assert(!o.gt("0.9", "1.0"))
        }

        "1.6.1 >= 1.8-dev" in {
          assert(!o.gteq("1.6.1", "1.8-dev"))
        }
      }
    }

    "show should render correctly" - {
      "1.0.0-beta" in {
        val test = DcosReleaseVersion(Version(1), List(Version(0), Version(0)), Some(Suffix("beta")))
        assertResult("1.0.0-beta")(test.show)
      }
      "1" in {
        val test = DcosReleaseVersion(Version(1))
        assertResult("1")(test.show)
      }
      "10-dev" in {
        val test = DcosReleaseVersion(Version(10), List.empty, Some(Suffix("dev")))
        assertResult("10-dev")(test.show)
      }
    }

    "Version" - {
      "should enforce that values are >= 0" - {
        "-1" in {
          assertAssertionError("assertion failed: Value -1 is not >= 0") {
            Version(-1)
          }
        }

        "-100" in {
          assertAssertionError("assertion failed: Value -100 is not >= 0") {
            Version(-100)
          }
        }

        "0" in {
          assertResult(0)(Version(0).value)
        }

        "1" in {
          assertResult(1)(Version(1).value)
        }

        "100" in {
          assertResult(100)(Version(100).value)
        }
      }
    }

    "Suffix" - {
      "enforces format constraints" - {
        "pass for" - {
          "beta" in {
            assertResult("beta")(Suffix("beta").value)
          }
          "BETA" in {
            assertResult("BETA")(Suffix("BETA").value)
          }
        }
        "fail for" - {
          "!" in {
            assertAssertionError("assertion failed: Value '!' does not conform to expected format ^[A-Za-z0-9]+$") {
              Suffix("!")
            }
          }
          "-" in {
            assertAssertionError("assertion failed: Value '-' does not conform to expected format ^[A-Za-z0-9]+$") {
              Suffix("-")
            }
          }
          "." in {
            assertAssertionError("assertion failed: Value '.' does not conform to expected format ^[A-Za-z0-9]+$") {
              Suffix(".")
            }
          }
        }
      }
    }
  }

  private[this] def assertAssertionError[T](expectedMessage: String)(f: => T): Unit = {
    Try { f } match {
      case Throw(ae: AssertionError) =>
        assertResult(expectedMessage)(ae.getMessage)
      case Throw(t) =>
        fail("unexpected throwable", t)
      case Return(r) =>
        fail(s"Return($r) when expected an assertion error")
    }
  }

}
