package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.twitter.finagle.http.Status

final case class CosmosException(
  error: CosmosError,
  status: Status,
  headers: Map[String, String],
  causedBy: Option[Throwable]
) extends RuntimeException(error.message, causedBy.orNull) {
  def errorResponse: ErrorResponse = {
    ErrorResponse(
      error.getClass.getSimpleName,
      error.message,
      error.data
    )
  }
}

object CosmosException {
  def apply(error: CosmosError): CosmosException = {
    CosmosException(error, Status.BadRequest, Map.empty, None)
  }

  def apply(error: CosmosError, causedBy: Throwable): CosmosException = {
    CosmosException(error, Status.BadRequest, Map.empty, Option(causedBy))
  }
}
