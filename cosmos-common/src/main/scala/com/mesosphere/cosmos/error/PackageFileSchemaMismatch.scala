package com.mesosphere.cosmos.error

import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._

final case class PackageFileSchemaMismatch(
  fileName: String,
  decodingFailure: DecodingFailure
) extends CosmosError {
  override def data: Option[JsonObject] = {
    Some(JsonObject.singleton("errorMessage", decodingFailure.getMessage().asJson))
  }
  override def message: String = s"Package file [$fileName] does not match schema"
}
