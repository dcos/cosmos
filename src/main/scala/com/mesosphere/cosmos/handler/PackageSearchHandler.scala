package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.PackageCache
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model._
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

import scala.util.matching.Regex

private[cosmos] class PackageSearchHandler(packageCache: PackageCache)
  (implicit searchRequestBodyDecoder: DecodeRequest[SearchRequest], encoder: Encoder[SearchResponse])
  extends EndpointHandler[SearchRequest, SearchResponse] {

  import PackageSearchHandler._

  val accepts: MediaType = MediaTypes.SearchRequest
  val produces: MediaType = MediaTypes.SearchResponse

  override def apply(request: SearchRequest): Future[SearchResponse] = {
    packageCache.getRepoIndex
      .map { repoIndex =>
        SearchResponse(search(repoIndex.packages, request.query))
      }
  }

}

private[cosmos] object PackageSearchHandler {

  private[cosmos] def search(packages: List[UniverseIndexEntry], queryOpt: Option[String]): List[UniverseIndexEntry] = {
    val wildcardSymbol = "*"
    queryOpt match {
      case None => packages
      case Some(query) =>
        if (query.contains(wildcardSymbol)) {
          packages.filter(searchRegexInPackageIndex(_, getRegex(query)))
        } else {
          packages.filter(searchPackageIndex(_, query.toLowerCase()))
        }
    }
  }

  private[this] def getRegex(query: String): Regex = {
    s"""^${query.replaceAll("\\*", ".*")}$$""".r
  }

  private[this] def searchRegexInPackageIndex(index: UniverseIndexEntry, regex: Regex): Boolean = {
    regex.findFirstIn(index.name).isDefined ||
      regex.findFirstIn(index.description).isDefined ||
        index.tags.exists(regex.findFirstIn(_).isDefined)
  }

  private[this] def searchPackageIndex(index: UniverseIndexEntry, query: String): Boolean= {
    index.name.toLowerCase().contains(query) ||
      index.description.toLowerCase().contains(query) ||
        index.tags.exists(_.toLowerCase().contains(query))
  }

}
