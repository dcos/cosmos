package com.mesosphere.cosmos.rpc.v2.model

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class InstallResponse(
  packageName: String,
  packageVersion: universe.v3.model.Version,
  appId: Option[AppId] = None,
  postInstallNotes: Option[String] = None,
  cli: Option[universe.v3.model.Cli] = None
)

object InstallResponse {
  implicit val encodeV2InstallResponse: Encoder[rpc.v2.model.InstallResponse] = {
    deriveEncoder[rpc.v2.model.InstallResponse]
  }
  implicit val decodeV2InstallResponse: Decoder[InstallResponse] = deriveDecoder[InstallResponse]
}
