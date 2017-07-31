package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class PackageRepositoryDeleteResponse(repositories: Seq[PackageRepository])

object PackageRepositoryDeleteResponse {
  implicit val decoder: Decoder[PackageRepositoryDeleteResponse] = {
    deriveDecoder[PackageRepositoryDeleteResponse]
  }
}
