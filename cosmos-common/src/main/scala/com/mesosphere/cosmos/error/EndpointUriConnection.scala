package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import com.netaporter.uri.Uri
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class EndpointUriConnection(
  name: String,
  destination: Uri,
  cause: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Could not access data at URI for endpoint [${name}]: ${destination}"
  }
}

object EndpointUriConnection {
  implicit val encoder: Encoder[EndpointUriConnection] = deriveEncoder
}
