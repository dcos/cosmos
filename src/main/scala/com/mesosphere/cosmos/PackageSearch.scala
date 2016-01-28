package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.{PackageIndex, SearchResponse, UniverseIndex}
import scala.util.matching.Regex

object PackageSearch {

  private[cosmos] def getSearchResults(
    query: Option[String],
    repoIndex: UniverseIndex
  ): SearchResponse = {
    SearchResponse(search(repoIndex.packages, query))
  }

  private[this] def search(packages: List[PackageIndex], queryOpt: Option[String]): List[PackageIndex] = {
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

  private[this] def searchRegexInPackageIndex(index: PackageIndex, regex: Regex): Boolean = {
    regex.findFirstIn(index.name).isDefined ||
      regex.findFirstIn(index.description).isDefined ||
        index.tags.exists(regex.findFirstIn(_).isDefined)
  }

  private[this] def searchPackageIndex(index: PackageIndex, query: String): Boolean= {
    index.name.toLowerCase().contains(query) ||
      index.description.toLowerCase().contains(query) ||
        index.tags.exists(_.toLowerCase().contains(query))
  }
}
