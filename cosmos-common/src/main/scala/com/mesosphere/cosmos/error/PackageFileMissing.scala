package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class PackageFileMissing(
  packageName: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Package file [$packageName] not found"
}

object PackageFileMissing {
  implicit val encoder: Encoder[PackageFileMissing] = deriveEncoder
}
