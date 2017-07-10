package com.mesosphere.cosmos.error

import io.circe.JsonObject

final case class JsonParsingError(
    circeError: io.circe.Error,
    jsonData: Option[JsonObject]
  ) extends CosmosError {
  override def data: Option[JsonObject] = jsonData
  override def message: String = "Unable to parse the string as a JSON value"
}
