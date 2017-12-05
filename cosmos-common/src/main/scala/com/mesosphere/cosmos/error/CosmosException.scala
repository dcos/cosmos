package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status

final case class CosmosException(
  error: CosmosError,
  status: Status,
  headers: Map[String, String],
  causedBy: Option[Throwable]
) extends RuntimeException(error.message, causedBy.orNull)

object CosmosException {
  def apply(error: CosmosError): CosmosException = {
    CosmosException(error, Status.BadRequest, Map.empty, None)
  }

  def apply(
    error: CosmosError,
    causedBy: Throwable,
    status: Status = Status.BadRequest
  ): CosmosException = {
    CosmosException(error, status, Map.empty, Option(causedBy))
  }
}
