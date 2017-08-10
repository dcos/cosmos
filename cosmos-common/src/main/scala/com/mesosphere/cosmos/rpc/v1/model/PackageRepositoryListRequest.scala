package com.mesosphere.cosmos.rpc.v1.model

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryListRequest()

object PackageRepositoryListRequest {
  implicit val encoder: Encoder[PackageRepositoryListRequest] = deriveEncoder
}
