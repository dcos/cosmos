package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedEncoder}
import com.mesosphere.cosmos.converter
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe

object MediaTypedEncoders {

  implicit val packageDescribeResponseEncoder: DispatchingMediaTypedEncoder[rpc.v2.model.DescribeResponse] = {
    DispatchingMediaTypedEncoder(Seq(
      MediaTypedEncoder(
        encoder = universe.v3.circe.Encoders.encodeV3Package,
        mediaType = MediaTypes.V2DescribeResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeDescribeResponse.contramap(
          converter.Response.v3PackageToDescribeResponse
        ),
        mediaType = MediaTypes.V1DescribeResponse
      )
    ))
  }

  implicit val packageInstallResponseEncoder: DispatchingMediaTypedEncoder[rpc.v2.model.InstallResponse] = {
    DispatchingMediaTypedEncoder(Seq(
      MediaTypedEncoder(
        encoder = rpc.v2.circe.Encoders.encodeV2InstallResponse,
        mediaType = MediaTypes.V2InstallResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeInstallResponse.contramap(
          converter.Response.v2InstallResponseToV1InstallResponse
        ),
        mediaType = MediaTypes.V1InstallResponse
      )
    ))
  }

}
