package com.mesosphere.cosmos.error

import com.mesosphere.universe.common.circe.Encoders._
import com.netaporter.uri.Uri
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class EndpointUriSyntax(
  name: String,
  destination: Uri,
  cause: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"URI for [${name}] has invalid syntax: ${destination}"
  }
}

object EndpointUriSyntax {
  implicit val encoder: Encoder[EndpointUriSyntax] = deriveEncoder
}
