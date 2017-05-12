package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Architectures(`x86-64`: Binary)

object Architectures {
  implicit val decodeArchitectures: Decoder[Architectures] = deriveDecoder[Architectures]
  implicit val encodeArchitectures: Encoder[Architectures] = deriveEncoder[Architectures]
}
