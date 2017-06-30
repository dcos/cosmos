package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class OptionsNotAllowed(msg : String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = msg
}

object OptionsNotAllowed {
  implicit val encoder: Encoder[OptionsNotAllowed] = deriveEncoder
}
