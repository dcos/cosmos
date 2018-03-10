package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.error.ResultOps
import com.mesosphere.http.MediaType
import com.mesosphere.http.MediaTypeSubType
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import org.scalatest.FreeSpec

class StorageEnvelopeSpec extends FreeSpec {
  import StorageEnvelopeSpec._

  "Decoding the result of an encoding should yield back the original data" in {
    val exp = TestClass("fooooo")
    val act = StorageEnvelope.decodeData[TestClass](
      StorageEnvelope.encodeData(exp)
    ).getOrThrow
    assertResult(exp)(act)
  }

  "Decoding the result of an encoding as a different type should throw an exception" in {
    val input = TestClass2("fooooo")
    intercept[CosmosException] {
      StorageEnvelope.decodeData[TestClass](StorageEnvelope.encodeData(input)).getOrThrow
    }
    ()
  }
}

object  StorageEnvelopeSpec {
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
