package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class ManagerNotSpecified() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "package does not define a manager section"
}

object ManagerNotSpecified {
  implicit val encoder: Encoder[OptionsNotAllowed] = deriveEncoder
}
