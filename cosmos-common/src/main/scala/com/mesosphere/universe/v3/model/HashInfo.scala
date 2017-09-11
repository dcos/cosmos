package com.mesosphere.universe.v3.model

import com.mesosphere.cosmos.circe.Decoders._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/hash
  */
case class HashInfo(algo: String, value: String)

object HashInfo {
  implicit val decodeHashInfo: Decoder[HashInfo] = deriveDecoder[HashInfo]
  implicit val encodeHashInfo: Encoder[HashInfo] = deriveEncoder[HashInfo]
}
