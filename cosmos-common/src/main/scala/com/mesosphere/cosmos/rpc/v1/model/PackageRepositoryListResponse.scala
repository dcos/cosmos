package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryListResponse(repositories: Seq[PackageRepository])

object PackageRepositoryListResponse {
  implicit val decoder: Decoder[PackageRepositoryListResponse] = deriveDecoder
  implicit val encoder: Encoder[PackageRepositoryListResponse] = deriveEncoder
  implicit val packageRepositoryListEncoder: DispatchingMediaTypedEncoder[PackageRepositoryListResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryListResponse)
}
