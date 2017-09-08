package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax._

case class PackagingVersion private(override val toString: String) extends AnyVal

object PackagingVersion {
  implicit val encodeV2PackagingVersion: Encoder[PackagingVersion] =
    Encoder.instance(_.toString.asJson)
  implicit val decodeV2PackagingVersion: Decoder[PackagingVersion] =
    Decoder.decodeString.map(PackagingVersion(_))
}
