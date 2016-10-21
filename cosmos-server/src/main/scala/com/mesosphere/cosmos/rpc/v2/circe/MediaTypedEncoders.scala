package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.finch.{DispatchingMediaTypedEncoder, MediaTypedEncoder}
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Try

object MediaTypedEncoders {

  implicit val packageDescribeResponseEncoder: DispatchingMediaTypedEncoder[universe.v3.model.PackageDefinition] = {
    DispatchingMediaTypedEncoder(Set(
      MediaTypedEncoder(
        encoder = rpc.v2.circe.Encoders.encodeV2DescribeResponse.contramap { (pkg: universe.v3.model.PackageDefinition) =>
          pkg.as[rpc.v2.model.DescribeResponse]
        },
        mediaType = MediaTypes.V2DescribeResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeDescribeResponse.contramap { (pkg: universe.v3.model.PackageDefinition) =>
          pkg.as[Try[rpc.v1.model.DescribeResponse]].get()
        },
        mediaType = MediaTypes.V1DescribeResponse
      )
    ))
  }

  implicit val packageRunResponseEncoder: DispatchingMediaTypedEncoder[rpc.v2.model.RunResponse] = {
    DispatchingMediaTypedEncoder(Set(
      MediaTypedEncoder(
        encoder = rpc.v2.circe.Encoders.encodeV2RunResponse,
        mediaType = MediaTypes.V2RunResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeRunResponse.contramap { (x: rpc.v2.model.RunResponse) =>
          x.as[Try[rpc.v1.model.RunResponse]].get()
        },
        mediaType = MediaTypes.V1RunResponse
      )
    ))
  }

}
