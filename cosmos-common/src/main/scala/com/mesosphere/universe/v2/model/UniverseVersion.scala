package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax._

case class UniverseVersion(override val toString: String) extends AnyVal

object UniverseVersion {
  implicit val decodeV2UniverseVersion: Decoder[UniverseVersion] =
    Decoder.decodeString.map(UniverseVersion(_))
  implicit val encodeV2UniverseVersion: Encoder[UniverseVersion] =
    Encoder.instance(_.toString.asJson)
}
