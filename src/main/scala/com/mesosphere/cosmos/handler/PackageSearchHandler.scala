package com.mesosphere.cosmos.handler

import scala.util.matching.Regex

import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.universe.UniverseIndexEntry

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
      SearchResponse(packages)
    }
  }
}
