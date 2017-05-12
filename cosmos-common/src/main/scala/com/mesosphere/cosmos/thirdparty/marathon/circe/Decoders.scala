package com.mesosphere.cosmos.thirdparty.marathon.circe

import com.mesosphere.cosmos.thirdparty.marathon.model._
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {
  implicit val decodeAppId: Decoder[AppId] = Decoder.decodeString.map(AppId(_))
  implicit val decodeMarathonApp: Decoder[MarathonApp] = deriveDecoder[MarathonApp]
  implicit val decodeMarathonAppContainer: Decoder[MarathonAppContainer] = deriveDecoder[MarathonAppContainer]
  implicit val decodeMarathonAppContainerDocker: Decoder[MarathonAppContainerDocker] = deriveDecoder[MarathonAppContainerDocker]
  implicit val decodeMarathonAppResponse: Decoder[MarathonAppResponse] = deriveDecoder[MarathonAppResponse]
  implicit val decodeMarathonAppsResponse: Decoder[MarathonAppsResponse] = deriveDecoder[MarathonAppsResponse]
  implicit val decodeMarathonError: Decoder[MarathonError] = deriveDecoder[MarathonError]
}
