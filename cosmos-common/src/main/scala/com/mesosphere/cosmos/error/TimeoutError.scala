package com.mesosphere.cosmos.error


import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class TimeoutError(
  operation: String,
  destination: String,
  timeout: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"$operation timed out on $destination after $timeout"
}

object TimeoutError {
  implicit val encoder: Encoder[TimeoutError] = deriveEncoder
}
