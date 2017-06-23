package com.mesosphere.cosmos.error

import io.circe.JsonObject

final case class ConcurrentAccess() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = {
    s"Retry operation. Operation didn't complete due to concurrent access."
  }
}
