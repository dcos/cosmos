package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class ListRequest(
  packageName: Option[String] = None,
  appId: Option[AppId] = None
)

object ListRequest {
  implicit val encodeListRequest: Encoder[ListRequest] = deriveEncoder[ListRequest]
  implicit val decodeListRequest: Decoder[ListRequest] = deriveDecoder[ListRequest]
  implicit val packageListDecoder: MediaTypedRequestDecoder[ListRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.ListRequest))
}
