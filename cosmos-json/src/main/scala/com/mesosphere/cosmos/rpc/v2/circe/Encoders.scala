package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders.encodeAppId
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v2.model.DescribeResponse
import io.circe.Encoder
import io.circe.generic.semiauto._

object Encoders {

  implicit val encodeV2InstallResponse: Encoder[rpc.v2.model.InstallResponse] = {
    deriveEncoder[rpc.v2.model.InstallResponse]
  }

  implicit val encodeV2DescribeResponse: Encoder[DescribeResponse] = {
    deriveEncoder[DescribeResponse]
  }
}
