package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/resource-schema.json
  */
case class Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None
)

object Resource {
  implicit val encodeV2Resource: Encoder[Resource] = deriveEncoder[Resource]
  implicit val decodeV2Resource: Decoder[Resource] = deriveDecoder[Resource]
}
