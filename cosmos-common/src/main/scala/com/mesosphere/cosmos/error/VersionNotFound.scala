package com.mesosphere.cosmos.error

import com.mesosphere.universe
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class VersionNotFound(
  packageName: String,
  packageVersion: universe.v3.model.Version
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Version [$packageVersion] of package [$packageName] not found"
  }
}

object VersionNotFound {
  implicit val encoder: Encoder[VersionNotFound] = deriveEncoder
}
