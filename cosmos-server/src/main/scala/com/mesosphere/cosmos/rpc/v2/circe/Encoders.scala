package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.cosmos.rpc
import io.circe.Encoder
import io.circe.generic.semiauto._

object Encoders {

  implicit val encodeV2InstallResponse: Encoder[rpc.v2.model.InstallResponse] = {
    deriveFor[rpc.v2.model.InstallResponse].encoder
  }

}
