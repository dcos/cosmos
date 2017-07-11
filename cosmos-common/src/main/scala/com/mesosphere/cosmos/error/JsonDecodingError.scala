package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class JsonDecodingError(
  typeName : String,
  decodingErrorMessage: String,
  decodeInput : String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Unable to decode the JSON value as a ${typeName}"
}

object JsonDecodingError {
  implicit val encoder: Encoder[JsonDecodingError] = deriveEncoder
}
