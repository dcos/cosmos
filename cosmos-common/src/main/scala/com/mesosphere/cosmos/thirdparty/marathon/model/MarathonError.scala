package com.mesosphere.cosmos.thirdparty.marathon.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class MarathonError(message: String, details: Option[List[JsonObject]])

object MarathonError {
  implicit val encodeMarathonError: Encoder[MarathonError] = deriveEncoder[MarathonError]
  implicit val decodeMarathonError: Decoder[MarathonError] = deriveDecoder[MarathonError]
}
