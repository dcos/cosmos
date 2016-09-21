package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.EnvelopeError
import com.mesosphere.cosmos.storage.Envelope._
import com.mesosphere.cosmos.test.TestUtil._
import org.scalatest.FreeSpec

class EnvelopeSpec extends FreeSpec {
  "Decoding the result of an encoding should yield back the original data" in {
    val exp = TestClass("fooooo")
    val act = decodeData[TestClass](encodeData(exp))
    assertResult(exp)(act)
  }

  "Decoding the result of an encoding as a different type should throw an exception" in {
    val input = TestClass2("fooooo")
    intercept[EnvelopeError] {
      decodeData[TestClass](encodeData(input))
    }
    ()
  }
}
