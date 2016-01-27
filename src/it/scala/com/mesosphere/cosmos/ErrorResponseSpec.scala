package com.mesosphere.cosmos

import cats.data.Xor
import com.twitter.io.Buf
import com.twitter.finagle.http.Status
import io.circe.parse._
import io.circe.syntax._
import io.circe.generic.auto._    // Required for auto-parsing case classes from JSON

class ErrorResponseSpec extends IntegrationSpec {

  "An ErrorResponse" should "be returned as the body when a request can't be parsed" in { service =>
    val requestString = Map("invalid" -> true).asJson.noSpaces
    val post = requestBuilder("v1/package/install").buildPost(Buf.Utf8(requestString))
    val response = service(post)

    assertResult(Status.BadRequest)(response.status)
    val Xor.Right(err) = decode[ErrorResponse](response.contentString)
    val msg = err.errors.head.message
    assert(msg.contains("body"))
    assert(msg.contains("decode value"))
    assert(msg.contains("name"))
  }

}
