package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class AppAlreadyUninstalling(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"A request to uninstall the service '$appId' is already in progress."
  }

  override def exception: CosmosException = {
    CosmosException(this, Status.Conflict, Map.empty, None)
  }
}

object AppAlreadyUninstalling {
  implicit val encoder: Encoder[AppAlreadyUninstalling] = deriveEncoder
}
