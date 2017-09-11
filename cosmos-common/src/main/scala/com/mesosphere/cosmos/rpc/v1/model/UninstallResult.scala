package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class UninstallResult(
  packageName: String,
  appId: AppId,
  packageVersion: Option[PackageDetailsVersion],
  postUninstallNotes: Option[String]
)

object UninstallResult {
  implicit val encodeUninstallResult: Encoder[UninstallResult] = deriveEncoder[UninstallResult]
  implicit val decodeUninstallResult: Decoder[UninstallResult] = deriveDecoder[UninstallResult]
}
