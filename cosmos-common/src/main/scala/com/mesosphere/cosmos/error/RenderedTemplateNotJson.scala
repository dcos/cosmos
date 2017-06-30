package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class RenderedTemplateNotJson(cause: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = cause
}

object RenderedTemplateNotJson {
  implicit val encoder: Encoder[RenderedTemplateNotJson] = deriveEncoder
}
