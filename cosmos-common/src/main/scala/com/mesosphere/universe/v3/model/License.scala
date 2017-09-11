package com.mesosphere.universe.v3.model

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class License(name: String, url: Uri)

object License {
  implicit val decodeLicense: Decoder[License] = deriveDecoder[License]
  implicit val encodeLicense: Encoder[License] = deriveEncoder[License]
}
