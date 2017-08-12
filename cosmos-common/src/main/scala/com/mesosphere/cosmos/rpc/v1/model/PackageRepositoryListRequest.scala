package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryListRequest()

object PackageRepositoryListRequest {
  implicit val encoder: Encoder[PackageRepositoryListRequest] = deriveEncoder
  implicit val decoder: Decoder[PackageRepositoryListRequest] = deriveDecoder
}
