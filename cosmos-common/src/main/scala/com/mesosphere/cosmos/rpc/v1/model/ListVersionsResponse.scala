package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.universe.v2.model.{PackageDetailsVersion, ReleaseVersion}
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class ListVersionsResponse(
  results: Map[PackageDetailsVersion, ReleaseVersion]
)

object ListVersionsResponse {
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] =
    deriveEncoder[ListVersionsResponse]
  implicit val decodeListVersionsResponse: Decoder[ListVersionsResponse] =
    deriveDecoder[ListVersionsResponse]
  implicit val packageListVersionsEncoder: DispatchingMediaTypedEncoder[ListVersionsResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ListVersionsResponse)
}
