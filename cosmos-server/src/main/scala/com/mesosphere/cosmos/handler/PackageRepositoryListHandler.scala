package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageSourcesStorage
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
