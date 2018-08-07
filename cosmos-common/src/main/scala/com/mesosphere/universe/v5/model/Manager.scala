package com.mesosphere.universe.v5.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Manager(
  packageName: String,
  minPackageVersion: Option[String],
)

object Manager {
  implicit val decodePlatforms: Decoder[Manager] = deriveDecoder[Manager]
  implicit val encodePlatforms: Encoder[Manager] = deriveEncoder[Manager]
}
