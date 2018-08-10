package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class UninstallRequest(
  packageName: String,
  appId: Option[AppId],
  all: Option[Boolean],
  managerId: Option[String] = None,
  packageVersion: Option[PackageDetailsVersion] = None)

object UninstallRequest {
  implicit val encodeUninstallRequest: Encoder[UninstallRequest] = deriveEncoder[UninstallRequest]
  implicit val decodeUninstallRequest: Decoder[UninstallRequest] = deriveDecoder[UninstallRequest]
  implicit val packageUninstallDecoder: MediaTypedRequestDecoder[UninstallRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.UninstallRequest))
}
