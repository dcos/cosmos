package com.mesosphere.cosmos.error

import com.mesosphere.universe
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class ServiceMarathonTemplateNotFound(
  packageName: String,
  packageVersion: universe.v3.model.Version
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Package: [$packageName] version: [$packageVersion] does not have a Marathon " +
    "template defined and can not be rendered"
  }
}

object ServiceMarathonTemplateNotFound {
  implicit val encoder: Encoder[ServiceMarathonTemplateNotFound] = deriveEncoder
}
