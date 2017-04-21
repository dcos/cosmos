package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.rpc.v2.model.DescribeResponse
import com.mesosphere.cosmos.rpc.v2.model.InstallResponse
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {

  implicit val decodeV2InstallResponse: Decoder[InstallResponse] = deriveDecoder[InstallResponse]

  implicit val decodeV2DescribeResponse: Decoder[DescribeResponse] = {
    deriveDecoder[DescribeResponse]
  }
}
