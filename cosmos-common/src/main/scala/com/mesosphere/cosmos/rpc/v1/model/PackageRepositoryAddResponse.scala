package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class PackageRepositoryAddResponse(repositories: Seq[PackageRepository])

object PackageRepositoryAddResponse {
  implicit val decoder: Decoder[PackageRepositoryAddResponse] = deriveDecoder
}
