package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Installation(
  appId: AppId,
  packageInformation: InstalledPackageInformation
)

object Installation {
  implicit val encodeInstallation: Encoder[Installation] = deriveEncoder[Installation]
  implicit val decodeInstallation: Decoder[Installation] = deriveDecoder[Installation]
}
