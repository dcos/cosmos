package com.mesosphere.cosmos.error

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, JsonObject}

final case class ErrorDuringUninstall(error: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Exception occured when processing uninstall: $error."
}

object ErrorDuringUninstall {
  implicit val encoder: Encoder[ErrorDuringUninstall] = deriveEncoder
}