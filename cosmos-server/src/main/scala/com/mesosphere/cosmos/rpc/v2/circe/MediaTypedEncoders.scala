package com.mesosphere.cosmos.rpc.v2.circe

import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedEncoder}
import com.mesosphere.cosmos.converter
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.internal
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Try

object MediaTypedEncoders {

  implicit val packageDescribeResponseEncoder: DispatchingMediaTypedEncoder[internal.model.PackageDefinition] = {
    DispatchingMediaTypedEncoder(Set(
      MediaTypedEncoder(
        encoder = rpc.v2.circe.Encoders.encodeV2DescribeResponse.contramap { (pkgDefinition: internal.model.PackageDefinition) =>
          converter.InternalPackageDefinition.internalPackageDefinitionToV2DescribeResponse(pkgDefinition)
        },
        mediaType = MediaTypes.V2DescribeResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeDescribeResponse.contramap[internal.model.PackageDefinition] { pkg =>
          pkg.as[Try[rpc.v1.model.DescribeResponse]].get()
        },
        mediaType = MediaTypes.V1DescribeResponse
      )
    ))
  }

  implicit val packageInstallResponseEncoder: DispatchingMediaTypedEncoder[rpc.v2.model.InstallResponse] = {
    DispatchingMediaTypedEncoder(Set(
      MediaTypedEncoder(
        encoder = rpc.v2.circe.Encoders.encodeV2InstallResponse,
        mediaType = MediaTypes.V2InstallResponse
      ),
      MediaTypedEncoder(
        encoder = rpc.v1.circe.Encoders.encodeInstallResponse.contramap { (x: rpc.v2.model.InstallResponse) =>
          x.as[Try[rpc.v1.model.InstallResponse]].get()
        },
        mediaType = MediaTypes.V1InstallResponse
      )
    ))
  }

}
