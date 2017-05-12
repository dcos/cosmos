package com.mesosphere.universe.v3.model

import com.mesosphere.universe.common.circe.Decoders._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Container(
  docker: Map[String, String]
)

object Container {
  implicit val decodeContainer: Decoder[Container] = deriveDecoder[Container]
  implicit val encodeContainer: Encoder[Container] = deriveEncoder[Container]
}
