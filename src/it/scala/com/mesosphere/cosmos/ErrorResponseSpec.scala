package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.http.MediaTypes
import com.twitter.io.Buf
import com.twitter.finagle.http.Status
import io.circe.parse._
import io.circe.syntax._
import com.mesosphere.cosmos.circe.Decoders._

class ErrorResponseSpec extends IntegrationSpec {

  "An ErrorResponse" should "be returned as the body when a request can't be parsed" in { service =>
    val requestString = Map("invalid" -> true).asJson.noSpaces
    val post = requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8(requestString))
    val response = service(post)

    assertResult(Status.BadRequest)(response.status)
    assertResult(MediaTypes.ErrorResponse.show)(response.headerMap("Content-Type"))
    val Xor.Right(err) = decode[ErrorResponse](response.contentString)
    val msg = err.message
    assert(msg.contains("body"))
    assert(msg.contains("decode value"))
    assert(msg.contains("packageName"))
  }

}
