package com.mesosphere.cosmos.rpc.v2.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import io.circe.Decoder
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class InstallRequest(
     packageName: String,
     packageVersion: Option[PackageDetailsVersion] = None,
     options: Option[JsonObject] = None,
     appId: Option[AppId] = None,
     managerId: Option[String] = None)

object InstallRequest {
  implicit val encodeInstallRequest: Encoder[InstallRequest] = deriveEncoder[InstallRequest]
  implicit val decodeInstallRequest: Decoder[InstallRequest] = deriveDecoder[InstallRequest]
  implicit val packageInstallDecoder: MediaTypedRequestDecoder[InstallRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.InstallRequest))
}
