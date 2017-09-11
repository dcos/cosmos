package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.finch.{DispatchingMediaTypedEncoder, MediaTypedEncoder}
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Try

object MediaTypedEncoders {

  implicit val packageDescribeResponseEncoder: DispatchingMediaTypedEncoder[universe.v4.model.PackageDefinition] = {
    DispatchingMediaTypedEncoder(
      Set(
        MediaTypedEncoder(
          encoder = rpc.v3.model.DescribeResponse.encoder.contramap(
            rpc.v3.model.DescribeResponse.apply
          ),
          mediaType = MediaTypes.V3DescribeResponse
        ),
        MediaTypedEncoder(
          encoder = rpc.v2.model.DescribeResponse.encodeV2DescribeResponse.contramap {
            (pkg: universe.v4.model.PackageDefinition) =>
              pkg.as[Try[rpc.v2.model.DescribeResponse]].get()
          },
          mediaType = MediaTypes.V2DescribeResponse
        )
      )
    )
  }

  implicit val packageInstallResponseEncoder: DispatchingMediaTypedEncoder[rpc.v2.model.InstallResponse] = {
    DispatchingMediaTypedEncoder(Set(
      MediaTypedEncoder(
        encoder = rpc.v2.model.InstallResponse.encodeV2InstallResponse,
        mediaType = MediaTypes.V2InstallResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.model.InstallResponse.encodeInstallResponse.contramap { (x: rpc.v2.model.InstallResponse) =>
          x.as[Try[rpc.v1.model.InstallResponse]].get()
        },
        mediaType = MediaTypes.V1InstallResponse
      )
    ))
  }

}
