package com.mesosphere.universe.v3.model

import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.syntax.EncoderOps

final case class Version(override val toString: String) extends AnyVal

object Version {
  implicit val encodePackageDefinitionVersion: Encoder[Version] = {
    Encoder.instance(_.toString.asJson)
  }
  implicit val keyEncodePackageDefinitionVersion: KeyEncoder[universe.v3.model.Version] = {
    KeyEncoder.instance(_.toString)
  }
  implicit val decodePackageDefinitionVersion: Decoder[Version] = {
    Decoder.decodeString.map(Version(_))
  }
  implicit val keyDecodePackageDefinitionVersion: KeyDecoder[universe.v3.model.Version] = {
    KeyDecoder.instance { s => Some(universe.v3.model.Version(s)) }
  }
}
