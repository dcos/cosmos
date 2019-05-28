package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.lemonlabs.uri.Uri
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import io.netty.handler.codec.http.HttpResponseStatus

final case class EndpointUriConnection(
  destination: Uri,
  cause: String
) extends CosmosError {
  override val status: HttpResponseStatus = HttpResponseStatus.BAD_GATEWAY
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Could not access data at URI ${destination}"
  }
}

object EndpointUriConnection {
  implicit val encoder: Encoder[EndpointUriConnection] = deriveEncoder
}
