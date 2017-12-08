package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status

final case class CosmosException(
  error: CosmosError,
  status: Status,
  headers: Map[String, String],
  causedBy: Option[Throwable]
) extends RuntimeException(error.message, causedBy.orNull)

object CosmosException {
  def apply(
    error: CosmosError,
    causedBy: Option[Throwable] = None
  ): CosmosException = {
    CosmosException(error, error.status, Map.empty, causedBy)
  }
}
