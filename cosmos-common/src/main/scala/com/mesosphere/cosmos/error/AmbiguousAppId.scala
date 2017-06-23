package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class AmbiguousAppId(packageName: String, appIds: List[AppId]) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Multiple apps named [$packageName] are installed: [${appIds.mkString(", ")}]"
  }
}

object AmbiguousAppId {
  implicit val encoder: Encoder[AmbiguousAppId] = deriveEncoder
}
