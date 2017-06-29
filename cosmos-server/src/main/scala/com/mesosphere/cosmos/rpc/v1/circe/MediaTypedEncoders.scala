package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._

object MediaTypedEncoders {

  implicit val capabilitiesEncoder: DispatchingMediaTypedEncoder[CapabilitiesResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.CapabilitiesResponse)

  implicit val packageListV1Encoder: DispatchingMediaTypedEncoder[ListResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.V1ListResponse)

  implicit val packageListVersionsEncoder: DispatchingMediaTypedEncoder[ListVersionsResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ListVersionsResponse)

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

}
