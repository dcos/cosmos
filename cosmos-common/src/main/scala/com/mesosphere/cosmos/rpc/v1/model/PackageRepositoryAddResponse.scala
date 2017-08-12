package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryAddResponse(repositories: Seq[PackageRepository])

object PackageRepositoryAddResponse {
  implicit val decoder: Decoder[PackageRepositoryAddResponse] = deriveDecoder
  implicit val encoder: Encoder[PackageRepositoryAddResponse] = deriveEncoder
}
