package com.mesosphere.cosmos.error

import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class InvalidLabelSchema(cause: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = cause
}

object InvalidLabelSchema {
  implicit val encoder: Encoder[InvalidLabelSchema] = deriveEncoder
}
