package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Capability(name: String)

object Capability {
  implicit val encodeCapability: Encoder[Capability] = deriveEncoder[Capability]
  implicit val decodeCapability: Decoder[Capability] = deriveDecoder[Capability]
}
