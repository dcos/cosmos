package com.mesosphere.cosmos.handler

import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model.{ListVersionsRequest, ListVersionsResponse}

private[cosmos] class ListVersionsHandler(
  packageCache: PackageCollection
)(implicit
  bodyDecoder: DecodeRequest[ListVersionsRequest],
  encoder: Encoder[ListVersionsResponse])
  extends EndpointHandler[ListVersionsRequest, ListVersionsResponse] {

  override def accepts: MediaType = MediaTypes.ListVersionsRequest
  override def produces: MediaType = MediaTypes.ListVersionsResponse

  override def apply(request: ListVersionsRequest): Future[ListVersionsResponse] = {
    packageCache
      .getPackageIndex(request.packageName)
      .map { packageInfo => ListVersionsResponse(packageInfo.versions) }
  }
}
