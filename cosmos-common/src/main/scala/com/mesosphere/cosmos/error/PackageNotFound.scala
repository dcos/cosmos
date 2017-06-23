package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class PackageNotFound(packageName: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Package [$packageName] not found"
}

object PackageNotFound {
  implicit val encoder: Encoder[PackageNotFound] = deriveEncoder
}
