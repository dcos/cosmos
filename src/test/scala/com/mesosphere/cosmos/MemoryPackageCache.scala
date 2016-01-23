package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.{DescribeRequest, PackageInfo, PackageFiles, UniverseIndex}
import com.twitter.util.Future
import io.circe.Json
import io.finch._

/** A package cache that stores all package information in memory. Useful for testing.
  *
  * @param packages the contents of the cache: package data indexed by package name
  */
final case class MemoryPackageCache(packages: Map[String, PackageFiles]) extends PackageCache {

  def getPackageFiles(
    packageName: String,
    version: Option[String]
  ): Future[PackageFiles] = {
    Future.value {
      packages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(a) => a
      }
    }
  }

  def getRepoIndex: Future[UniverseIndex] = {
    Future.exception(RepositoryNotFound())
  }

  def getPackageIndex(
    packageName: String
  ): Future[PackageInfo] = {
    Future.exception(PackageNotFound(packageName))
  }

  def getPackageDescribe(
    describeRequest: DescribeRequest
  ): Future[Output[Json]] = {
    Future(Ok(Json.empty))
  }
}
