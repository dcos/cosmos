package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class OptionsNotAllowed() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "No schema available to validate the provided options"
}

object OptionsNotAllowed {
  implicit val encoder: Encoder[OptionsNotAllowed] = deriveEncoder
}
