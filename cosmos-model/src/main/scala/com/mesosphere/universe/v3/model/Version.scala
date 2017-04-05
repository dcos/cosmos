package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps

final case class Version(override val toString: String) extends AnyVal

object Version {
  implicit val encodePackageDefinitionVersion: Encoder[Version] = {
    Encoder.instance(_.toString.asJson)
  }
  implicit val decodePackageDefinitionVersion: Decoder[Version] = {
    Decoder.decodeString.map(Version(_))
  }
}
