package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class MarathonAppNotFound(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Unable to locate service with marathon appId: '$appId'"
}

object MarathonAppNotFound {
  implicit val encoder: Encoder[MarathonAppNotFound] = deriveEncoder
}
