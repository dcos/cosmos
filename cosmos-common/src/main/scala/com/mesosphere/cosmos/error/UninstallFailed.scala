package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.{Encoder, JsonObject}
import io.circe.generic.semiauto.deriveEncoder

final case class UninstallFailed(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Uninstall of app [$appId] failed."
}

object UninstallFailed {
  implicit val encoder: Encoder[UninstallFailed] = deriveEncoder
}

