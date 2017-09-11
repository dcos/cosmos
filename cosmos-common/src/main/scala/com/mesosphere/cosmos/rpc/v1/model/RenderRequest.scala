package com.mesosphere.cosmos.rpc.v1.model

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

case class RenderRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion] = None,
  options: Option[JsonObject] = None,
  appId: Option[AppId] = None
)

object RenderRequest{
  implicit val encodeRenderRequest: Encoder[RenderRequest] = deriveEncoder[RenderRequest]
  implicit val decodeRenderRequest: Decoder[RenderRequest] = deriveDecoder[RenderRequest]
  implicit val packageRenderDecoder: MediaTypedRequestDecoder[RenderRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.RenderRequest))
}
