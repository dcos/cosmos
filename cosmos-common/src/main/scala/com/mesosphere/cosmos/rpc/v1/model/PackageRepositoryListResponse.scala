package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class PackageRepositoryListResponse(repositories: Seq[PackageRepository])

object PackageRepositoryListResponse {
  implicit val decoder: Decoder[PackageRepositoryListResponse] = {
    deriveDecoder[PackageRepositoryListResponse]
  }
}
