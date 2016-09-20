package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.EnvelopeError
import com.mesosphere.cosmos.circe.{MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http.{MediaType, MediaTypeSubType}
import com.mesosphere.cosmos.storage.Envelope._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.scalatest.FreeSpec

class EnvelopeSpec extends FreeSpec {
  import EnvelopeSpec._

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

object  EnvelopeSpec {
  private case class TestClass(name: String)
  private val TestClassMt = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.package.repository.test-class", Some("json")),
    Map(
      "charset" -> "utf-8",
      "version" -> "v1"
    )
  )
  private implicit val decodeTestClass: Decoder[TestClass] = deriveDecoder
  private implicit val encodeTestClass: Encoder[TestClass] = deriveEncoder
  private implicit val mtdTestClass: MediaTypedDecoder[TestClass] =
    MediaTypedDecoder(TestClassMt)
  private implicit val mteTestClass: MediaTypedEncoder[TestClass] =
    MediaTypedEncoder(TestClassMt)

  private case class TestClass2(name: String)
  private val TestClass2Mt = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.package.repository.test-class2", Some("json")),
    Map(
      "charset" -> "utf-8",
      "version" -> "v1"
    )
  )
  private implicit val encodeTestClass2: Encoder[TestClass2] = deriveEncoder
  private implicit val mteTestClass2: MediaTypedEncoder[TestClass2] =
    MediaTypedEncoder(TestClass2Mt)

}
