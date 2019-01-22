package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.lemonlabs.uri.Uri
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class EndpointUriSyntax(
  destination: Uri,
  cause: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"URI for [${destination}] has invalid syntax"
  }
}

object EndpointUriSyntax {
  implicit val encoder: Encoder[EndpointUriSyntax] = deriveEncoder
}
