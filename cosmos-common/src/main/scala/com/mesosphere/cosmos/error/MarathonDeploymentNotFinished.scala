package com.mesosphere.cosmos.error

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, JsonObject}

final case class MarathonDeploymentNotFinished(deploymentId: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Waiting for deployment '$deploymentId' to finish, but it's still running."
}

object MarathonDeploymentNotFinished {
  implicit val encoder: Encoder[MarathonDeploymentNotFinished] = deriveEncoder
}
