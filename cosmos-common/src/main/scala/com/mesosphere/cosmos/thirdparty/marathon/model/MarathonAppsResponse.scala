package com.mesosphere.cosmos.thirdparty.marathon.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class MarathonAppsResponse(apps: List[MarathonApp])

object MarathonAppsResponse {
  implicit val decodeMarathonAppsResponse: Decoder[MarathonAppsResponse] =
    deriveDecoder[MarathonAppsResponse]
}
