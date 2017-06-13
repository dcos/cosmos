package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Package file [$fileName] is not JSON: $parseError"
}

object PackageFileNotJson {
  implicit val encoder: Encoder[PackageFileNotJson] = deriveEncoder
}
