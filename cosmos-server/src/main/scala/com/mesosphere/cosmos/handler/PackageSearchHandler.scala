package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future

private[cosmos] final class PackageSearchHandler(
  packageCache: PackageCollection
) extends EndpointHandler[SearchRequest, SearchResponse] {

  override def apply(request: SearchRequest)(implicit
    session: RequestSession
  ): Future[SearchResponse] = {
    packageCache.search(request.query) map { packages =>
      val sortedPackages = packages.sortBy(p => (!p.selected.getOrElse(false), p.name.toLowerCase))
      SearchResponse(sortedPackages)
    }
  }

}
