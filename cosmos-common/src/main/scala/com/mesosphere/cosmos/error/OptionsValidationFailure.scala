package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class OptionsValidationFailure(errors: Iterable[Json]) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = "Options JSON failed validation"
}

object OptionsValidationFailure {
  implicit val encoder: Encoder[OptionsValidationFailure] = deriveEncoder
}
