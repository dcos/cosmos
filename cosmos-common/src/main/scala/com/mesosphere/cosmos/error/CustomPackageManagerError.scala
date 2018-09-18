package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class CustomPackageManagerError(
  managerId: String,
  statusCode: Int,
  responseContent: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Custom manager '$managerId' returned error: '$statusCode' '$responseContent'"
}

object CustomPackageManagerError {
  implicit val encoder: Encoder[CustomPackageManagerError] = deriveEncoder
}
