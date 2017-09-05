package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class InstallResponse(
  packageName: String,
  packageVersion: PackageDetailsVersion,
  appId: AppId
)

object InstallResponse {
  implicit val encodeInstallResponse: Encoder[InstallResponse] = deriveEncoder[InstallResponse]
  implicit val decodeInstallResponse: Decoder[InstallResponse] = deriveDecoder[InstallResponse]
  implicit val packageInstallV1Encoder: DispatchingMediaTypedEncoder[InstallResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.V1InstallResponse)
}
