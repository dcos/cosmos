package com.mesosphere.cosmos.error

import com.mesosphere.universe
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class VersionUpgradeNotSupportedInOpen(
  requested: Option[universe.v3.model.Version],
  actual: universe.v3.model.Version
) extends CosmosError {
  override def message: String =
    s"Version Upgrades are an Enterprise DC/OS feature only"

  override def data: Option[JsonObject] = CosmosError.deriveData(this)
}

object VersionUpgradeNotSupportedInOpen {
  implicit val encoder: Encoder[VersionUpgradeNotSupportedInOpen] = deriveEncoder
}
