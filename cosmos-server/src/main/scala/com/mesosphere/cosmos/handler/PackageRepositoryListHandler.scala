package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.rpc.v1.model.{PackageRepositoryListRequest, PackageRepositoryListResponse}
import com.twitter.util.Future

private[cosmos] final class PackageRepositoryListHandler(
  sourcesStorage: PackageSourcesStorage
) extends EndpointHandler[PackageRepositoryListRequest, PackageRepositoryListResponse] {

  override def apply(req: PackageRepositoryListRequest)(implicit
    session: RequestSession
  ): Future[PackageRepositoryListResponse] = {
    sourcesStorage.read().map(PackageRepositoryListResponse(_))
  }

}
