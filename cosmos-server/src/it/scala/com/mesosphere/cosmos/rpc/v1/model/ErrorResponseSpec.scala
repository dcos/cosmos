package com.mesosphere.cosmos.rpc.v1.model

import cats.data.Xor
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import io.circe.jawn._
import io.circe.syntax._
import org.scalatest.FreeSpec

class ErrorResponseSpec extends FreeSpec {

  "An ErrorResponse should be returned as the body when a request can't be parsed" in {
    val requestString = Map("invalid" -> true).asJson.noSpaces
    val post = CosmosClient.requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.V1InstallResponse.show)
      .buildPost(Buf.Utf8(requestString))
    val response = CosmosClient(post)

    assertResult(Status.BadRequest)(response.status)
    assertResult(MediaTypes.ErrorResponse.show)(response.headerMap("Content-Type"))
    val Xor.Right(err) = decode[ErrorResponse](response.contentString)
    val msg = err.message
    assert(msg.contains("body"))
    assert(msg.contains("decode value"))
    assert(msg.contains("packageName"))
  }

}
