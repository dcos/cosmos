package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedEncoder}
import com.mesosphere.cosmos.converter
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.internal

object MediaTypedEncoders {

  implicit val packageDescribeResponseEncoder: DispatchingMediaTypedEncoder[internal.model.PackageDefinition] = {
    DispatchingMediaTypedEncoder(Seq(
      MediaTypedEncoder(
        encoder = rpc.v2.circe.Encoders.encodeV2DescribeResponse.contramap { (pkgDefinition: internal.model.PackageDefinition) =>
          converter.Universe.internalPackageDefinitionToV2DescribeResponse(pkgDefinition)
        },
        mediaType = MediaTypes.V2DescribeResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeDescribeResponse.contramap[internal.model.PackageDefinition] { pkg =>
          converter.Response.packageDefinitionToDescribeResponse(pkg)
        },
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
