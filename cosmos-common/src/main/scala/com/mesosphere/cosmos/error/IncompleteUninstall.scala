package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class IncompleteUninstall(
  packageName: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Incomplete uninstall of package [$packageName] due to Mesos unavailability"
  }
}

object IncompleteUninstall {
  implicit val encoder: Encoder[IncompleteUninstall] = deriveEncoder
}
