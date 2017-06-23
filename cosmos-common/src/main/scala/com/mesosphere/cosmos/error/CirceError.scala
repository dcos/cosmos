package com.mesosphere.cosmos.error

import io.circe.JsonObject

final case class CirceError(circeError: io.circe.Error) extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = circeError.getMessage
}
