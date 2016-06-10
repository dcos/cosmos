package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedEncoder}
import com.mesosphere.cosmos.converter
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe

object MediaTypedEncoders {

  implicit val packageDescribeEncoder: DispatchingMediaTypedEncoder[rpc.v2.model.DescribeResponse] = {
    DispatchingMediaTypedEncoder(Seq(
      MediaTypedEncoder(
        encoder = universe.v3.circe.Encoders.encodeV3Package,
        mediaType = MediaTypes.V2DescribeResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeDescribeResponse.contramap(converter.Response.v3PackageToDescribeResponse),
        mediaType = MediaTypes.V1DescribeResponse
      )
    ))
  }

}
