package com.mesosphere.cosmos.thirdparty.marathon.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class MarathonAppContainerDocker(image: String, network: Option[String])

object MarathonAppContainerDocker {
  implicit val encodeMarathonAppContainerDocker: Encoder[MarathonAppContainerDocker] = deriveEncoder[MarathonAppContainerDocker]
  implicit val decodeMarathonAppContainerDocker: Decoder[MarathonAppContainerDocker] = deriveDecoder[MarathonAppContainerDocker]
}
