package com.mesosphere.cosmos.thirdparty.marathon.circe

import com.mesosphere.cosmos.thirdparty.marathon.model._
import io.circe.Encoder
import io.circe.generic.semiauto._

object Encoders {
  implicit val encodeMarathonAppContainer: Encoder[MarathonAppContainer] = deriveEncoder[MarathonAppContainer]
  implicit val encodeMarathonAppContainerDocker: Encoder[MarathonAppContainerDocker] = deriveEncoder[MarathonAppContainerDocker]
  implicit val encodeMarathonError: Encoder[MarathonError] = deriveEncoder[MarathonError]
}
