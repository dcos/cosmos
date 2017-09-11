package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/command-schema.json
  */
case class Command(pip: List[String])

object Command {
  implicit val encodeV2Command: Encoder[Command] = deriveEncoder[Command]
  implicit val decodeV2Command: Decoder[Command] = deriveDecoder[Command]
}
