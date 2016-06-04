package com.mesosphere.cosmos.thirdparty.marathon.circe

import com.mesosphere.cosmos.thirdparty.marathon.model._
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {
  implicit val encodeAppId: Encoder[AppId] = Encoder.instance(_.toString.asJson)
  implicit val encodeMarathonApp: Encoder[MarathonApp] = deriveFor[MarathonApp].encoder
  implicit val encodeMarathonAppContainer: Encoder[MarathonAppContainer] = deriveFor[MarathonAppContainer].encoder
  implicit val encodeMarathonAppContainerDocker: Encoder[MarathonAppContainerDocker] = deriveFor[MarathonAppContainerDocker].encoder
  implicit val encodeMarathonAppResponse: Encoder[MarathonAppResponse] = deriveFor[MarathonAppResponse].encoder
  implicit val encodeMarathonAppsResponse: Encoder[MarathonAppsResponse] = deriveFor[MarathonAppsResponse].encoder
  implicit val encodeMarathonError: Encoder[MarathonError] = deriveFor[MarathonError].encoder
}
