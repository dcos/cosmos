package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class FailedToStartUninstall(appId: AppId, explanation: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Failed to start an uninstall for the service '$appId': $explanation"
  }
}

object FailedToStartUninstall {
  implicit val encoder: Encoder[FailedToStartUninstall] = deriveEncoder
}
