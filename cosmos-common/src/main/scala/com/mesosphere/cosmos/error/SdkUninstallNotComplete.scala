package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, JsonObject}

final case class SdkUninstallNotComplete(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"SDK scheduler [$appId] uninstall plan is not finished"
}

object SdkUninstallNotComplete {
  implicit val encoder: Encoder[SdkUninstallNotComplete] = deriveEncoder
}

