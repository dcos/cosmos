package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class MarathonAppDeleteError(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Error while deleting marathon app '$appId'"
}

object MarathonAppDeleteError {
  implicit val encoder: Encoder[MarathonAppDeleteError] = deriveEncoder
}
