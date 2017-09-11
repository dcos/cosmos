package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryDeleteResponse(repositories: Seq[PackageRepository])

object PackageRepositoryDeleteResponse {
  implicit val decoder: Decoder[PackageRepositoryDeleteResponse] = deriveDecoder
  implicit val encoder: Encoder[PackageRepositoryDeleteResponse] = deriveEncoder
  implicit val packageRepositoryDeleteEncoder: DispatchingMediaTypedEncoder[PackageRepositoryDeleteResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryDeleteResponse)
}
