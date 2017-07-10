package com.mesosphere.cosmos.error

import io.circe.Json
import io.circe.JsonObject

final case class JsonDecodingError(
  typeName : String = "",
  json : String
) extends CosmosError {
  override def data: Option[JsonObject] = Json.fromFields(
    List(
      ("typeName",Json.fromString(typeName)),
      ("string", Json.fromString(json))
    )).asObject
  override def message: String = s"Unable to decode the JSON value as a ${typeName}"
}
