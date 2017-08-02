package com.mesosphere.cosmos.http

import org.scalatest.Assertion
import org.scalatest.FreeSpec
import scala.language.implicitConversions

class MediaTypeOpsSpec extends FreeSpec {

  "MediaTypeOps.compatibleIgnoringParameters(MediaType, MediaType) should" - {
    "pass for" - {
      behave like compatibleIgnoringParametersSuccessSpec()
    }

    "fail for" - {
      behave like compatibleIgnoringParametersFailureSpec()
    }
  }

  "MediaTypeOps.compatible(MediaType, MediaType) should" - {
    "pass for" - {
      behave like compatibleSuccessSpec()
    }

    "fail for" - {
      behave like compatibleFailureSpec()
    }
  }

  "MediaTypeOps.isCompatibleWith(MediaType) should" - {
    "pass for" - {
      behave like compatibleSuccessSpec()
    }

    "fail for" - {
      behave like compatibleFailureSpec()
    }
  }

  "MediaTypeOps.qValue(MediaType) should" - {
    "return the correct value when present and in the valid range" - {
      "0.0" in {
        assertResult(0.0)(MediaTypeOps.qValue("text/html;q=0.0").quality)
      }
      "0.25" in {
        assertResult(0.25)(MediaTypeOps.qValue("text/html;q=0.25").quality)
      }
      "1.0" in {
        assertResult(1.0)(MediaTypeOps.qValue("text/html;q=1.0").quality)
      }
    }

    "return default value when the value is present and outside the valid range" - {
      "-0.009" in {
        assertResult(QualityValue.default)(MediaTypeOps.qValue("text/html;q=-0.009"))
      }
      "1.1" in {
        assertResult(QualityValue.default)(MediaTypeOps.qValue("text/html;q=1.1"))
      }
    }
  }

  "MediaTypeOps.mediaTypeOrdering should" - {
    import MediaTypeOps.mediaTypeOrdering
    "priority 1: qvalue" in {
      val expected: List[MediaType] = List(
        "text/html;level=1;q=1.0",
        "text/html;level=3;q=0.7",
        "text/html;q=0.7",
        "image/jpeg;q=0.5",
        "text/html;level=2;q=0.4",
        "text/plain;q=0.3"
      )

      val test: List[MediaType] = List(
        "image/jpeg;q=0.5",
        "text/html;level=1;q=1.0",
        "text/html;level=2;q=0.4",
        "text/html;level=3;q=0.7",
        "text/html;q=0.7",
        "text/plain;q=0.3"
      )

      val sorted = test.sorted
      assertResult(expected)(sorted)
    }

    "priority 2: type" in {
      val expected: List[MediaType] = List(
        "image/jpeg;q=0.4",
        "text/html;q=0.4",
        "text/plain;q=0.3",
        "text/html;level=1;q=0.2"
      )

      val test: List[MediaType] = List(
        "image/jpeg;q=0.4",
        "text/html;level=1;q=0.2",
        "text/html;q=0.4",
        "text/plain;q=0.3"
      )

      val sorted = test.sorted
      assertResult(expected)(sorted)
    }

    "priority 3: sub-type" in {
      val expected: List[MediaType] = List(
        "image/jpeg;q=0.4",
        "text/html;q=0.4",
        "text/plain;q=0.4",
        "text/html;level=1;q=0.2"
      )

      val test: List[MediaType] = List(
        "image/jpeg;q=0.4",
        "text/html;level=1;q=0.2",
        "text/html;q=0.4",
        "text/plain;q=0.4"
      )

      val sorted = test.sorted
      assertResult(expected)(sorted)
    }

    "priority 4: sub-type suffix" in {
      val expected: List[MediaType] = List(
        "application/vnd.custom+json",
        "application/vnd.custom+xml",
        "image/jpeg"
      )

      val test: List[MediaType] = List(
        "application/vnd.custom+xml",
        "image/jpeg",
        "application/vnd.custom+json"
      )

      val sorted = test.sorted
      assertResult(expected)(sorted)
    }

    "priority 5: number of parameters" in {
      val expected: List[MediaType] = List(
        "application/vnd.custom+json;charset=utf-8;version=v1",
        "application/vnd.custom+json;version=v1",
        "image/jpeg"
      )

      val test: List[MediaType] = List(
        "image/jpeg",
        "application/vnd.custom+json;version=v1",
        "application/vnd.custom+json;charset=utf-8;version=v1"
      )

      val sorted = test.sorted
      assertResult(expected)(sorted)
    }

    "ignore qvalue as a parameter when comparing number of parameters" in {
      val expected: List[MediaType] = List(
        "application/vnd.custom+json;charset=utf-8;version=v2",
        "application/vnd.custom+json;version=v1;q=1.0"
      )

      val test: List[MediaType] = List(
        "application/vnd.custom+json;version=v1;q=1.0",
        "application/vnd.custom+json;charset=utf-8;version=v2"
      )

      val sorted = test.sorted
      assertResult(expected)(sorted)
    }

  }

  "Compatibility should ignore any present qValue" in {
    shouldSucceed("type/subtype;something=awesome;q=0.9", "type/subtype;something=awesome")
  }

  "MediaTypeOps.qValue should return 1.0 if not specified" in {
    assertResult(QualityValue(1.0))(MediaTypeOps.qValue("*/*"))
  }

  private[this] def compatibleIgnoringParametersSuccessSpec(): Unit = {

    "type/subtype & type/subtype" in {
      shouldSucceed("type/subtype", "type/subtype")
    }

    "type/subtype+suffix & type/subtype+suffix" in {
      shouldSucceed("type/subtype+suffix", "type/subtype+suffix")
    }

  }

  private[this] def compatibleIgnoringParametersFailureSpec(): Unit = {

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

  private[this] def compatibleSuccessSpec(): Unit = {

    behave like compatibleIgnoringParametersSuccessSpec()

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

  private[this] def compatibleFailureSpec(): Unit = {

    behave like compatibleIgnoringParametersFailureSpec()

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

  private[this] def shouldSucceed(expected: MediaType, actual: MediaType): Assertion = {
    assertMatch(expected, actual, shouldPass = true)
  }
  private[this] def shouldFail(expected: MediaType, actual: MediaType): Assertion = {
    assertMatch(expected, actual, shouldPass = false)
  }
  private[this] def assertMatch(
    expected: MediaType,
    actual: MediaType,
    shouldPass: Boolean
  ): Assertion = {
    assert(MediaTypeOps.compatible(expected, actual) == shouldPass)
  }

  private[this] implicit def stringToMediaType(s: String): MediaType = {
    MediaTypeParser.parseUnsafe(s)
  }

}
