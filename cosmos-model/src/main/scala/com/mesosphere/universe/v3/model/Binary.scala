package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/cliInfo
  */
case class Binary(kind: String, url: String, contentHash: List[HashInfo])

object Binary {
  implicit val decodeBinary: Decoder[Binary] = deriveDecoder[Binary]
  implicit val encodeBinary: Encoder[Binary] = deriveEncoder[Binary]
}
