package com.mesosphere.cosmos.thirdparty.marathon.circe

import com.mesosphere.cosmos.thirdparty.marathon.model._
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {
  implicit val encodeAppId: Encoder[AppId] = Encoder.instance(_.toString.asJson)
  implicit val encodeMarathonApp: Encoder[MarathonApp] = deriveEncoder[MarathonApp]
  implicit val encodeMarathonAppContainer: Encoder[MarathonAppContainer] = deriveEncoder[MarathonAppContainer]
  implicit val encodeMarathonAppContainerDocker: Encoder[MarathonAppContainerDocker] = deriveEncoder[MarathonAppContainerDocker]
  implicit val encodeMarathonAppResponse: Encoder[MarathonAppResponse] = deriveEncoder[MarathonAppResponse]
  implicit val encodeMarathonAppsResponse: Encoder[MarathonAppsResponse] = deriveEncoder[MarathonAppsResponse]
  implicit val encodeMarathonError: Encoder[MarathonError] = deriveEncoder[MarathonError]
}
