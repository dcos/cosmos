package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Cli(
  binaries: Option[Platforms]
)

object Cli {
  implicit val decodeCli: Decoder[Cli] = deriveDecoder[Cli]
  implicit val encodeCli: Encoder[Cli] = deriveEncoder[Cli]
}
