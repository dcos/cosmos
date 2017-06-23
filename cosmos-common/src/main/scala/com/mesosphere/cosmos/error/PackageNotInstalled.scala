package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class PackageNotInstalled(packageName: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Package [$packageName] is not installed"
}

object PackageNotInstalled {
  implicit val encoder: Encoder[PackageNotInstalled] = deriveEncoder
}
