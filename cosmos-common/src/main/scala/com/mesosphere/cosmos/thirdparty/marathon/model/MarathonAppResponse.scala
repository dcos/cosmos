package com.mesosphere.cosmos.thirdparty.marathon.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class MarathonAppResponse(app: MarathonApp)

object MarathonAppResponse {
  implicit val decodeMarathonAppResponse: Decoder[MarathonAppResponse] =
    deriveDecoder[MarathonAppResponse]
}
