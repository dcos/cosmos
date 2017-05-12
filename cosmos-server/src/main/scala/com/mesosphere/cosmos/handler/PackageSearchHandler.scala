package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.twitter.util.Future

private[cosmos] final class PackageSearchHandler(
  packageCache: PackageCollection
) extends EndpointHandler[rpc.v1.model.SearchRequest, rpc.v1.model.SearchResponse] {

  override def apply(request: rpc.v1.model.SearchRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.SearchResponse] = {
    packageCache.search(request.query) map { packages =>
      val sortedPackages = packages.sortBy(p => (!p.selected.getOrElse(false), p.name.toLowerCase))
      rpc.v1.model.SearchResponse(sortedPackages)
    }

  }
}
