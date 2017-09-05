package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Container(docker: Map[String, String])

object Container {
  implicit val encodeV2Container: Encoder[Container] = deriveEncoder[Container]
  implicit val decodeV2Container: Decoder[Container] = deriveDecoder[Container]
}
