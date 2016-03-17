package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] class PackageSearchHandler(
  packageCache: PackageCollection
)(implicit
  searchRequestBodyDecoder: DecodeRequest[SearchRequest],
  encoder: Encoder[SearchResponse]
) extends EndpointHandler[SearchRequest, SearchResponse] {

  val accepts: MediaType = MediaTypes.SearchRequest
  val produces: MediaType = MediaTypes.SearchResponse

  override def apply(request: SearchRequest): Future[SearchResponse] = {
    packageCache.search(request.query) map { packages =>
      val sortedPackages = packages.sortBy(p => (!p.promoted.getOrElse(false), p.name.toLowerCase))
      SearchResponse(sortedPackages)
    }
  }
}
