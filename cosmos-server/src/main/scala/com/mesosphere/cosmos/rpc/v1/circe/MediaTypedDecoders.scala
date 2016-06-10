package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.circe.MediaTypedDecoder
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model._
import io.finch.circe._

object MediaTypedDecoders {

  implicit val packageListDecoder: MediaTypedDecoder[ListRequest] =
    MediaTypedDecoder(MediaTypes.ListRequest)

  implicit val packageListVersionsDecoder: MediaTypedDecoder[ListVersionsRequest] =
    MediaTypedDecoder(MediaTypes.ListVersionsRequest)

  implicit val packageDescribeDecoder: MediaTypedDecoder[DescribeRequest] =
    MediaTypedDecoder(MediaTypes.DescribeRequest)

  implicit val packageInstallDecoder: MediaTypedDecoder[InstallRequest] =
    MediaTypedDecoder(MediaTypes.InstallRequest)

  implicit val packageRenderDecoder: MediaTypedDecoder[RenderRequest] =
    MediaTypedDecoder(MediaTypes.RenderRequest)

  implicit val packageRepositoryAddDecoder: MediaTypedDecoder[PackageRepositoryAddRequest] =
    MediaTypedDecoder(MediaTypes.PackageRepositoryAddRequest)

  implicit val packageRepositoryDeleteDecoder: MediaTypedDecoder[PackageRepositoryDeleteRequest] =
    MediaTypedDecoder(MediaTypes.PackageRepositoryDeleteRequest)

  implicit val packageRepositoryListDecoder: MediaTypedDecoder[PackageRepositoryListRequest] =
    MediaTypedDecoder(MediaTypes.PackageRepositoryListRequest)

  implicit val packageSearchDecoder: MediaTypedDecoder[SearchRequest] =
    MediaTypedDecoder(MediaTypes.SearchRequest)

  implicit val packageUninstallDecoder: MediaTypedDecoder[UninstallRequest] =
    MediaTypedDecoder(MediaTypes.UninstallRequest)

}
