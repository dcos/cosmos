package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import io.netty.handler.codec.http.HttpResponseStatus

final case class IncompleteUninstall(
  packageName: String,
  override val status: HttpResponseStatus = HttpResponseStatus.SERVICE_UNAVAILABLE
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Incomplete uninstall of package [$packageName] due to Mesos unavailability"
  }

}

object IncompleteUninstall {
  implicit val encoder: Encoder[IncompleteUninstall] = deriveEncoder
}
