package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.PackageCache
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model.{ListVersionsRequest, ListVersionsResponse}
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] class ListVersionsHandler(packageCache: PackageCache)
  (implicit bodyDecoder: DecodeRequest[ListVersionsRequest], encoder: Encoder[ListVersionsResponse])
  extends EndpointHandler[ListVersionsRequest, ListVersionsResponse] {
  override def accepts: MediaType = MediaTypes.ListVersionsRequest
  override def produces: MediaType = MediaTypes.ListVersionsResponse

  override def apply(request: ListVersionsRequest): Future[ListVersionsResponse] = {
    packageCache
      .getPackageIndex(request.packageName)
      .map { packageInfo => ListVersionsResponse(packageInfo.versions) }
  }
}
