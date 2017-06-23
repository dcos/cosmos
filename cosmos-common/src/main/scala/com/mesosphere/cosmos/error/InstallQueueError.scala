package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class InstallQueueError(override val message: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
}

object InstallQueueError {
  implicit val encoder: Encoder[InstallQueueError] = deriveEncoder
}
