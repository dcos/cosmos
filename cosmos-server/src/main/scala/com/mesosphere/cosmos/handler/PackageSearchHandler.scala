package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.{PackageCollection, V3PackageCollection}
import com.mesosphere.cosmos.rpc.v1.model.SearchRequest
import com.mesosphere.cosmos.rpc.v1.model.SearchResponse
import com.mesosphere.cosmos.rpc.v1.model.V3SearchResponse
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

// TODO (version): Rename to PackageSearchHandler
private[cosmos] final class V3PackageSearchHandler(
  packageCache: V3PackageCollection
) extends EndpointHandler[SearchRequest, V3SearchResponse] {

  override def apply(request: SearchRequest)(implicit
    session: RequestSession
  ): Future[V3SearchResponse] = {
    packageCache.search(request.query) map { packages =>
      val sortedPackages = packages.sortBy(p => (!p.selected.getOrElse(false), p.name.toLowerCase))
      V3SearchResponse(sortedPackages)
    }

  }
}
