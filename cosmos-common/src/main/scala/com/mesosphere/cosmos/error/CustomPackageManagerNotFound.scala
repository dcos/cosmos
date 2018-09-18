package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class CustomPackageManagerNotFound(managerId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"the custom manager: '$managerId', is not installed for this package'"
}

object CustomPackageManagerNotFound {
  implicit val encoder: Encoder[CustomPackageManagerNotFound] = deriveEncoder
}