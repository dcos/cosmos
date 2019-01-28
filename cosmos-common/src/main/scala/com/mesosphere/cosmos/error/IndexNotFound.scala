package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.lemonlabs.uri.Uri
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class IndexNotFound(repoUri: Uri) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Index file missing for repo [$repoUri]"
}

object IndexNotFound {
  implicit val encoder: Encoder[IndexNotFound] = deriveEncoder
}
