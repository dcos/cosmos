package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.{ListVersionsRequest, ListVersionsResponse}
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future

private[cosmos] class ListVersionsHandler(
  packageCache: PackageCollection
) extends EndpointHandler[ListVersionsRequest, ListVersionsResponse] {

  override def apply(request: ListVersionsRequest)(implicit
    session: RequestSession
  ): Future[ListVersionsResponse] = {
    packageCache
      .getPackageIndex(request.packageName)
      .map { packageInfo => ListVersionsResponse(packageInfo.versions) }
  }

}
