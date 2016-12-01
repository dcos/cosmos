package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model._

object MediaTypedRequestDecoders {

  implicit val packageListDecoder: MediaTypedRequestDecoder[ListRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.ListRequest))

  implicit val packageListVersionsDecoder: MediaTypedRequestDecoder[ListVersionsRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.ListVersionsRequest))

  implicit val packageDescribeDecoder: MediaTypedRequestDecoder[DescribeRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.DescribeRequest))

  implicit val packageInstallDecoder: MediaTypedRequestDecoder[InstallRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.InstallRequest))

  implicit val packageRenderDecoder: MediaTypedRequestDecoder[RenderRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.RenderRequest))

  implicit val packageRepositoryAddDecoder: MediaTypedRequestDecoder[PackageRepositoryAddRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.PackageRepositoryAddRequest))

  implicit val packageRepositoryDeleteDecoder: MediaTypedRequestDecoder[PackageRepositoryDeleteRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.PackageRepositoryDeleteRequest))

  implicit val packageRepositoryListDecoder: MediaTypedRequestDecoder[PackageRepositoryListRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.PackageRepositoryListRequest))

  implicit val packageSearchDecoder: MediaTypedRequestDecoder[SearchRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.SearchRequest))

  implicit val packageUninstallDecoder: MediaTypedRequestDecoder[UninstallRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.UninstallRequest))

  implicit val serviceStartDecoder: MediaTypedRequestDecoder[ServiceStartRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.ServiceStartRequest))

}
