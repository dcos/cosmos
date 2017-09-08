package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax._

case class ReleaseVersion(override val toString: String) extends AnyVal

object ReleaseVersion {
  implicit val encodeV2PackageRevision: Encoder[ReleaseVersion] =
    Encoder.instance(_.toString.asJson)
  implicit val decodeV2PackageRevision: Decoder[ReleaseVersion] =
    Decoder.decodeString.map(ReleaseVersion(_))
}
