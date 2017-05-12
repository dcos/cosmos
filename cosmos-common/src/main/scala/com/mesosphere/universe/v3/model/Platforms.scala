package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Platforms(
  windows: Option[Architectures],
  linux: Option[Architectures],
  darwin: Option[Architectures]
)

object Platforms {
  implicit val decodePlatforms: Decoder[Platforms] = deriveDecoder[Platforms]
  implicit val encodePlatforms: Encoder[Platforms] = deriveEncoder[Platforms]
}
