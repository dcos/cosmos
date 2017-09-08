package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class License(name: String, url: String)

object License {
  implicit val encodeV2License: Encoder[License] = deriveEncoder[License]
  implicit val decodeV2License: Decoder[License] = deriveDecoder[License]
}
