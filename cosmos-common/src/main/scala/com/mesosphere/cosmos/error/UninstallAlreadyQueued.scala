package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class UninstallAlreadyQueued(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"AppId [$appId] is already queued for uninstall"
}

object UninstallAlreadyQueued {
  implicit val encoder: Encoder[UninstallAlreadyQueued] = deriveEncoder
}
