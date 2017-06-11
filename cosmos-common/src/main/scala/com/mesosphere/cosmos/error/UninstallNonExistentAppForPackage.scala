package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class UninstallNonExistentAppForPackage(
  packageName: String,
  appId: AppId
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Package [$packageName] with id [$appId] is not installed"
}

object UninstallNonExistentAppForPackage {
  implicit val encoder: Encoder[UninstallNonExistentAppForPackage] = deriveEncoder
}
