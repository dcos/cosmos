package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.rpc
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import io.circe.Decoder
import io.circe.jawn.decode
import org.scalatest.Matchers

sealed trait CosmosResponse[+A]

object CosmosResponse {
  def apply[Resp: Decoder](response: Response): CosmosResponse[Resp] = {
    if (response.status.code / 100 == 2) {
      decode[Resp](response.contentString) match {
        case Left(_) =>
          Matchers.fail("Could not decode as successful response: " + response.contentString)
        case Right(successfulResponse) =>
          SuccessCosmosResponse(successfulResponse)
      }
    } else {
      decode[rpc.v1.model.ErrorResponse](response.contentString) match {
        case Left(_) =>
          Matchers.fail("Could not decode as error response: " + response.contentString)
        case Right(errorResponse) =>
          ErrorCosmosResponse(response.status, errorResponse)
      }
    }
  }
}

final case class SuccessCosmosResponse[A](body: A) extends CosmosResponse[A]

final case class ErrorCosmosResponse(
  status: Status,
  error: rpc.v1.model.ErrorResponse
) extends CosmosResponse[Nothing]
