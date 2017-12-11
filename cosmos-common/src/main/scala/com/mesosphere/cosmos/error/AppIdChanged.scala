package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class AppIdChanged(newAppId: AppId, oldAppId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"The new appId $newAppId must be equal to the old appId $oldAppId"
  }
}

object AppIdChanged {
  implicit val encoder: Encoder[AppIdChanged] = deriveEncoder
}
