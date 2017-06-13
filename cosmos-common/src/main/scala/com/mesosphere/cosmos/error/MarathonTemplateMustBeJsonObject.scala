package com.mesosphere.cosmos.error

import io.circe.JsonObject

case object MarathonTemplateMustBeJsonObject extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "Rendered Marathon JSON must be a JSON object"
}
