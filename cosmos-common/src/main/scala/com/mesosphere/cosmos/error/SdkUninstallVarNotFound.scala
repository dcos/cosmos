package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders.encodeAppId


final case class SdkUninstallVarNotFound(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"AppId [$appId] is already queued for uninstall"
}

object SdkUninstallVarNotFound {
  implicit val encoder: Encoder[SdkUninstallVarNotFound] = deriveEncoder
}
