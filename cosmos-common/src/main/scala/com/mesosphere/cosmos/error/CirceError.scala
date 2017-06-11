package com.mesosphere.cosmos.error

import io.circe.JsonObject

// TODO: This doesn't look right. Doesn't look like very useful exception
// TODO: Doesn't look like we need the full circeError
final case class CirceError(circeError: io.circe.Error) extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = circeError.getMessage
}
