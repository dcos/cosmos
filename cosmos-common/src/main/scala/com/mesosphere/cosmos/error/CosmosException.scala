package com.mesosphere.cosmos.error

final case class CosmosException(
  error: CosmosError,
  headers: Map[String, String],
  causedBy: Option[Throwable]
) extends RuntimeException(error.message, causedBy.orNull)

object CosmosException {
  def apply(error: CosmosError): CosmosException = {
    CosmosException(error, Map.empty, None)
  }

  def apply(error: CosmosError, causedBy: Throwable): CosmosException = {
    CosmosException(error, Map.empty, Option(causedBy))
  }
}
