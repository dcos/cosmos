package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class JsonParsingError(
  underlyingType : String,
  parsingErrorMessage: String,
  parsingInput: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = "Unable to parse the string as a JSON value"
}

object JsonParsingError {
  implicit val encoder: Encoder[JsonParsingError] = deriveEncoder
}
