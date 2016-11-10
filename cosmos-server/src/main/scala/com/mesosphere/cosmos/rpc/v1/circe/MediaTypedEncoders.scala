package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.internal.circe.Encoders._
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.model.Repository
import com.mesosphere.universe.{MediaTypes => UMediaTypes}

object MediaTypedEncoders {

  implicit val repositoryServeEncoder: DispatchingMediaTypedEncoder[Repository] =
    DispatchingMediaTypedEncoder(UMediaTypes.UniverseV3Repository)

  implicit val packagePublishEncoder: DispatchingMediaTypedEncoder[PublishResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PublishResponse)

  implicit val capabilitiesEncoder: DispatchingMediaTypedEncoder[CapabilitiesResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.CapabilitiesResponse)

  implicit val packageListV1Encoder: DispatchingMediaTypedEncoder[ListResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.V1ListResponse)

  implicit val packageListVersionsEncoder: DispatchingMediaTypedEncoder[ListVersionsResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ListVersionsResponse)

  implicit val packageDescribeV1Encoder: DispatchingMediaTypedEncoder[DescribeResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.V1DescribeResponse)

  implicit val packageInstallV1Encoder: DispatchingMediaTypedEncoder[InstallResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.V1InstallResponse)

  implicit val packageRenderEncoder: DispatchingMediaTypedEncoder[RenderResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.RenderResponse)

  implicit val packageRepositoryAddEncoder: DispatchingMediaTypedEncoder[PackageRepositoryAddResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryAddResponse)

  implicit val packageRepositoryDeleteEncoder: DispatchingMediaTypedEncoder[PackageRepositoryDeleteResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryDeleteResponse)

  implicit val packageRepositoryListEncoder: DispatchingMediaTypedEncoder[PackageRepositoryListResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryListResponse)

  implicit val packageSearchEncoder: DispatchingMediaTypedEncoder[SearchResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.SearchResponse)

  implicit val packageUninstallEncoder: DispatchingMediaTypedEncoder[UninstallResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.UninstallResponse)

  implicit val serviceStartEncoder: DispatchingMediaTypedEncoder[ServiceStartResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ServiceStartResponse)

}
