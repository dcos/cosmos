package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.ErrorResponse
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {

  implicit val decodeErrorResponse: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]

}
