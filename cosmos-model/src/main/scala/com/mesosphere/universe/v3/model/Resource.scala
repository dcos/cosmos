package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v20resource
  */
case class V2Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None
)

object V2Resource {
  implicit val encodeV2Resource: Encoder[V2Resource] = deriveEncoder[V2Resource]
  implicit val decodeV3V2Resource: Decoder[V2Resource] = deriveDecoder[V2Resource]
}

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v30resource
  */
case class V3Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None,
  cli: Option[Cli] = None
)

object V3Resource {
  implicit val encodeV3Resource: Encoder[V3Resource] = deriveEncoder[V3Resource]
  implicit val decodeV3V3Resource: Decoder[V3Resource] = deriveDecoder[V3Resource]
}
