package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
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

  override def status: Status = Status.ServiceUnavailable
}

object IncompleteUninstall {
  implicit val encoder: Encoder[IncompleteUninstall] = deriveEncoder
}
