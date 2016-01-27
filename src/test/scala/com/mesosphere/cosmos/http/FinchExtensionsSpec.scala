package com.mesosphere.cosmos.http

import com.twitter.util.Try
import org.scalatest.FreeSpec

import scala.language.implicitConversions

class FinchExtensionsSpec extends FreeSpec {

  "beTheExpectedType(MediaType) should" - {
    "pass for" - {
      "type/subtype & type/subtype" in {
        shouldSucceed("type/subtype", "type/subtype")
      }

      "type/subtype+suffix & type/subtype+suffix" in {
        shouldSucceed("type/subtype+suffix", "type/subtype+suffix")
      }

      "type/subtype;k=v & type/subtype;k=v" in  {
        shouldSucceed("type/subtype;k=v", "type/subtype;k=v")
      }

      "type/subtype;charset=utf-8 & type/subtype;charset=utf-8" in {
        shouldSucceed("type/subtype;charset=utf-8", "type/subtype;charset=utf-8")
      }

      "type/subtype & type/subtype;charset=utf-8" in {
        shouldSucceed("type/subtype", "type/subtype;charset=utf-8")
      }

      "type/subtype;charset=utf-8 & type/subtype;charset=utf-8;foo=bar" in {
        shouldSucceed("type/subtype;charset=utf-8", "type/subtype;charset=utf-8;foo=bar")
      }
    }

    "fail for" - {
      "type/subtype+suffix & type/subtype" in {
        shouldFail("type/subtype+suffix", "type/subtype")
      }

      "type/subtype & type/subtype+suffix" in {
        shouldFail("type/subtype", "type/subtype+suffix")
      }

      "type/subtype;charset=utf-8 & type/subtype" in {
        shouldFail("type/subtype;charset=utf-8", "type/subtype")
      }

      "type/subtype;charset=utf-8 & type/subtype;foo=bar" in {
        shouldFail("type/subtype;charset=utf-8", "type/subtype;foo=bar")
      }
    }
  }

  private[this] def shouldSucceed(expected: Try[MediaType], actual: Try[MediaType]): Unit = {
    assertMatch(expected, actual, shouldPass = true)
  }
  private[this] def shouldFail(expected: Try[MediaType], actual: Try[MediaType]): Unit = {
    assertMatch(expected, actual, shouldPass = false)
  }
  private[this] def assertMatch(expected: Try[MediaType], actual: Try[MediaType], shouldPass: Boolean): Unit = {
    assert(FinchExtensions.beTheExpectedType(expected.get())(actual.get()) == shouldPass)
  }

  private[this] implicit def stringToMediaType(s: String): Try[MediaType] = {
    MediaType.parse(s)
  }
}
