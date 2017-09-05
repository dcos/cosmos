package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Assets(
  uris: Option[Map[String, String]], // GitHub issue #58
  container: Option[Container]
)

object Assets {
  implicit val encodeV2Assets: Encoder[Assets] = deriveEncoder[Assets]
  implicit val decodeV2Assets: Decoder[Assets] = deriveDecoder[Assets]
}
