package com.mesosphere.cosmos

import cats.syntax.option._
import com.twitter.util.Future
import io.circe.Json

/** A package cache that stores all package information in memory. Useful for testing.
  *
  * @param packages the contents of the cache: Marathon JSON files indexed by package name
  */
final case class MemoryPackageCache(packages: Map[String, Json]) extends PackageCache {

  def getMarathonJson(packageName: String, version: Option[String]): Future[CosmosResult[Json]] = {
    Future.value(packages.get(packageName).toRightXor(errorNel(PackageNotFound(packageName))))
  }

}
