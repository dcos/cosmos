package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Assets(
  uris: Option[Map[String, String]],
  container: Option[Container]
)

object Assets {
  implicit val decodeAssets: Decoder[Assets] = deriveDecoder[Assets]
  implicit val encodeAssets: Encoder[Assets] = deriveEncoder[Assets]
}
