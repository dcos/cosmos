package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.http.MediaTypeOps.mediaTypeToMediaTypeOps
import org.scalatest.FreeSpec

import scala.language.implicitConversions

class MediaTypeOpsSpec extends FreeSpec {

  "MediaTypeOps.compatibleIgnoringParameters(MediaType, MediaType) should" - {
    "pass for" - {
      behave like compatibleIgnoringParametersSuccessSpec(MediaTypeOps.compatibleIgnoringParameters)
    }

    "fail for" - {
      behave like compatibleIgnoringParametersFailureSpec(MediaTypeOps.compatibleIgnoringParameters)
    }
  }

  "MediaTypeOps.compatible(MediaType, MediaType) should" - {
    "pass for" - {
      behave like compatibleSuccessSpec(MediaTypeOps.compatible)
    }

    "fail for" - {
      behave like compatibleFailureSpec(MediaTypeOps.compatible)
    }
  }

  "MediaTypeOps.isCompatibleWith(MediaType) should" - {
    "pass for" - {
      behave like compatibleSuccessSpec((m1, m2) => m1.isCompatibleWith(m2))
    }

    "fail for" - {
      behave like compatibleFailureSpec((m1, m2) => m1.isCompatibleWith(m2))
    }
  }

  private[this] def compatibleIgnoringParametersSuccessSpec(
    testedFn: (MediaType, MediaType) => Boolean
  ): Unit = {

    "type/subtype & type/subtype" in {
      shouldSucceed("type/subtype", "type/subtype")
    }

    "type/subtype+suffix & type/subtype+suffix" in {
      shouldSucceed("type/subtype+suffix", "type/subtype+suffix")
    }

  }

  private[this] def compatibleIgnoringParametersFailureSpec(
    testedFn: (MediaType, MediaType) => Boolean
  ): Unit = {

    "type/subtype & otherType/subtype" in {
      shouldFail("type/subtype", "otherType/subtype")
    }

    "type/subtype & type/otherSubType" in {
      shouldFail("type/subtype", "type/otherSubType")
    }

    "type/subtype+suffix & type/subtype" in {
      shouldFail("type/subtype+suffix", "type/subtype")
    }

    "type/subtype & type/subtype+suffix" in {
      shouldFail("type/subtype", "type/subtype+suffix")
    }

  }

  private[this] def compatibleSuccessSpec(testedFn: (MediaType, MediaType) => Boolean): Unit = {

    behave like compatibleIgnoringParametersSuccessSpec(testedFn)

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

  private[this] def compatibleFailureSpec(testedFn: (MediaType, MediaType) => Boolean): Unit = {

    behave like compatibleIgnoringParametersFailureSpec(testedFn)

    "type/subtype;charset=utf-8 & type/subtype" in {
      shouldFail("type/subtype;charset=utf-8", "type/subtype")
    }

    "type/subtype;charset=utf-8 & type/subtype;charset=utf-16" in {
      shouldFail("type/subtype;charset=utf-8", "type/subtype;charset=utf-16")
    }

    "type/subtype;charset=utf-8 & type/subtype;foo=utf-8" in {
      shouldFail("type/subtype;charset=utf-8", "type/subtype;foo=utf-8")
    }

    "type/subtype;charset=utf-8 & type/subtype;foo=bar" in {
      shouldFail("type/subtype;charset=utf-8", "type/subtype;foo=bar")
    }

  }

  private[this] def shouldSucceed(expected: MediaType, actual: MediaType): Unit = {
    assertMatch(expected, actual, shouldPass = true)
  }
  private[this] def shouldFail(expected: MediaType, actual: MediaType): Unit = {
    assertMatch(expected, actual, shouldPass = false)
  }
  private[this] def assertMatch(expected: MediaType, actual: MediaType, shouldPass: Boolean): Unit = {
    assert(MediaTypeOps.compatible(expected, actual) == shouldPass)
  }

  private[this] implicit def stringToMediaType(s: String): MediaType = {
    MediaType.parse(s).get()
  }

}
