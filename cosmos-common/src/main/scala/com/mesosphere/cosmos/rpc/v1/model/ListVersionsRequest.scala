package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class ListVersionsRequest(
  packageName: String,
  includePackageVersions: Boolean
)

object ListVersionsRequest {
  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] =
    deriveEncoder[ListVersionsRequest]
  implicit val decodeListVersionsRequest: Decoder[ListVersionsRequest] =
    deriveDecoder[ListVersionsRequest]
  implicit val packageListVersionsDecoder: MediaTypedRequestDecoder[ListVersionsRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.ListVersionsRequest))
}
