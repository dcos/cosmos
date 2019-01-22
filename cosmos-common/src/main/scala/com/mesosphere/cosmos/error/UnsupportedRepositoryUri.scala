package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.lemonlabs.uri.Uri
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class UnsupportedRepositoryUri(uri: Uri) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Repository URI [$uri] uses an unsupported scheme. Only http and https are supported"
  }
}

object UnsupportedRepositoryUri {
  implicit val encoder: Encoder[UnsupportedRepositoryUri] = deriveEncoder
}
