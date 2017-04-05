package com.mesosphere.universe.v3.model

import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.common.circe.Encoders._
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import java.nio.ByteBuffer
import java.util.Base64

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/marathon
  */
case class Marathon(v2AppMustacheTemplate: ByteBuffer)

object Marathon {
  implicit val decodeMarathon: Decoder[Marathon] = deriveDecoder[Marathon]
  implicit val encodeMarathon: Encoder[Marathon] = deriveEncoder[Marathon]
}
