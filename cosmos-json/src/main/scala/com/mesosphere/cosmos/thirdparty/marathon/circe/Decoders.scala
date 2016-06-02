package com.mesosphere.cosmos.thirdparty.marathon.circe

import com.mesosphere.cosmos.thirdparty.marathon.model._
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {
  implicit val decodeAppId: Decoder[AppId] = Decoder.decodeString.map(AppId(_))
  implicit val decodeMarathonApp: Decoder[MarathonApp] = deriveFor[MarathonApp].decoder
  implicit val decodeMarathonAppContainer: Decoder[MarathonAppContainer] = deriveFor[MarathonAppContainer].decoder
  implicit val decodeMarathonAppContainerDocker: Decoder[MarathonAppContainerDocker] = deriveFor[MarathonAppContainerDocker].decoder
  implicit val decodeMarathonAppResponse: Decoder[MarathonAppResponse] = deriveFor[MarathonAppResponse].decoder
  implicit val decodeMarathonAppsResponse: Decoder[MarathonAppsResponse] = deriveFor[MarathonAppsResponse].decoder
  implicit val decodeMarathonError: Decoder[MarathonError] = deriveFor[MarathonError].decoder
}
