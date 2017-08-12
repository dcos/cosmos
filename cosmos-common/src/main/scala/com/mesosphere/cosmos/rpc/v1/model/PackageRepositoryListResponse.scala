package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryListResponse(repositories: Seq[PackageRepository])

object PackageRepositoryListResponse {
  implicit val decoder: Decoder[PackageRepositoryListResponse] = deriveDecoder
  implicit val encoder: Encoder[PackageRepositoryListResponse] = deriveEncoder
}
