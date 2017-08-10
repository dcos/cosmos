package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.HttpErrorResponse
import com.mesosphere.cosmos.rpc
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import io.circe.Decoder
import io.circe.jawn.decode
import org.scalatest.Matchers

sealed trait CosmosResponse[+A] {
  def get(): A
}


object CosmosResponse {
  def apply[Res: Decoder](response: Response): CosmosResponse[Res] = {
    if (response.status.code / 100 == 2) {
      decode[Res](response.contentString) match {
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

final case class SuccessCosmosResponse[A](body: A) extends CosmosResponse[A] {
  override def get(): A = body
}

final case class ErrorCosmosResponse(
  status: Status,
  error: rpc.v1.model.ErrorResponse
) extends CosmosResponse[Nothing] {
  override def get(): Nothing = throw HttpErrorResponse(status, error)
}
