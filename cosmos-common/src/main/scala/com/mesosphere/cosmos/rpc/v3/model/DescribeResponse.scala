package com.mesosphere.cosmos.rpc.v3.model

import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

final case class DescribeResponse(
  `package`: universe.v4.model.PackageDefinition
)

object DescribeResponse {
  implicit val encoder: Encoder[DescribeResponse] = deriveEncoder[DescribeResponse]
  implicit val decoder: Decoder[DescribeResponse] = deriveDecoder[DescribeResponse]
}
