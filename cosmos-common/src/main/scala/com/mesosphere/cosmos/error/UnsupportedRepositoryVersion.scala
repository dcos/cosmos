package com.mesosphere.cosmos.error

import com.mesosphere.universe
import com.mesosphere.universe.v2.circe.Encoders._
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class UnsupportedRepositoryVersion(
  version: universe.v2.model.UniverseVersion
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Repository version [$version] is not supported"
}

object UnsupportedRepositoryVersion {
  implicit val encoder: Encoder[UnsupportedRepositoryVersion] = deriveEncoder
}
