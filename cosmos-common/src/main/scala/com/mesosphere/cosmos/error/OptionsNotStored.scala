package com.mesosphere.cosmos.error

import io.circe.JsonObject

final case class OptionsNotStored() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = {
    "The options of the current service were not stored"
  }
}
