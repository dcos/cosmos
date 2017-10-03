package com.mesosphere.http

import org.scalatest.FreeSpec
import scala.util.Failure
import scala.util.Success

class QualityValueSpec extends FreeSpec {

  "QualityValue.parse should" - {

    "error when input that is not a double in base10" in {
      val Failure(err) = QualityValue.parse("asd")
      assertResult("Unexpected q value 'asd'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "error when the provide value is a double and is outside the valid range" in {
      val Failure(err) = QualityValue.parse("1.1")
      assertResult("Unexpected q value '1.1'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "error when more than 3 decimal places" in {
      val Failure(err) = QualityValue.parse("0.1234")
      assertResult("Unexpected q value '0.1234'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "error when does not start with 0 or 1" in {
      val Failure(err) = QualityValue.parse("7.1234")
      assertResult("Unexpected q value '7.1234'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places")(err.getMessage)
    }

    "succeed when input is" - {
      "0.0" in {
        assertResult(Success(QualityValue(0.0)))(QualityValue.parse("0.0"))
      }
      "0.25" in {
        assertResult(Success(QualityValue(0.25)))(QualityValue.parse("0.25"))
      }
      "1.0" in {
        assertResult(Success(QualityValue(1.0)))(QualityValue.parse("1.0"))
      }
    }

  }

}

