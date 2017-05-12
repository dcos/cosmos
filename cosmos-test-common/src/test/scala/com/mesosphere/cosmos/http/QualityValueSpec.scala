package com.mesosphere.cosmos.http

import org.scalatest.FreeSpec

import com.twitter.util.{Return, Throw}

class QualityValueSpec extends FreeSpec {

  "QualityValue.parse should" - {

    "error when input that is not a double in base10" in {
      val Throw(err) = QualityValue.parse("asd")
      assertResult("Unexpected q value 'asd'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "error when the provide value is a double and is outside the valid range" in {
      val Throw(err) = QualityValue.parse("1.1")
      assertResult("Unexpected q value '1.1'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "error when more than 3 decimal places" in {
      val Throw(err) = QualityValue.parse("0.1234")
      assertResult("Unexpected q value '0.1234'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "error when does not start with 0 or 1" in {
      val Throw(err) = QualityValue.parse("7.1234")
      assertResult("Unexpected q value '7.1234'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "succeed when input is" - {
      "0.0" in {
        assertResult(Return(QualityValue(0.0)))(QualityValue.parse("0.0"))
      }
      "0.25" in {
        assertResult(Return(QualityValue(0.25)))(QualityValue.parse("0.25"))
      }
      "1.0" in {
        assertResult(Return(QualityValue(1.0)))(QualityValue.parse("1.0"))
      }
    }

  }

}

