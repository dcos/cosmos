package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.syntax._

case class PackageDetailsVersion(override val toString: String) extends AnyVal

object PackageDetailsVersion {
  implicit val encodeV2PackageDetailsVersion: Encoder[PackageDetailsVersion] =
    Encoder.instance(_.toString.asJson)
  implicit val keyEncodeV2PackageDetailsVersion: KeyEncoder[PackageDetailsVersion] =
    KeyEncoder.instance(_.toString)

  implicit val decodeV2PackageDetailsVersion: Decoder[PackageDetailsVersion] =
    Decoder.decodeString.map(PackageDetailsVersion(_))
  implicit val keyDecodeV2PackageDetailsVersion: KeyDecoder[PackageDetailsVersion] =
    KeyDecoder.decodeKeyString.map(PackageDetailsVersion(_))
}
