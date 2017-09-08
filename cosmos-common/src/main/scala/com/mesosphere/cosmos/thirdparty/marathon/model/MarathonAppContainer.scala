package com.mesosphere.cosmos.thirdparty.marathon.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class MarathonAppContainer(`type`: String, docker: Option[MarathonAppContainerDocker])

object MarathonAppContainer {
  implicit val encodeMarathonAppContainer: Encoder[MarathonAppContainer] = deriveEncoder[MarathonAppContainer]
  implicit val decodeMarathonAppContainer: Decoder[MarathonAppContainer] = deriveDecoder[MarathonAppContainer]
}
