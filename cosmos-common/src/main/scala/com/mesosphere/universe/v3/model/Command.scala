package com.mesosphere.universe.v3.model

import com.mesosphere.cosmos.circe.Decoders._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/command
  */
case class Command(pip: List[String])

object Command {
  implicit val decodeCommand: Decoder[Command] = deriveDecoder[Command]
  implicit val encodeCommand: Encoder[Command] = deriveEncoder[Command]
}
