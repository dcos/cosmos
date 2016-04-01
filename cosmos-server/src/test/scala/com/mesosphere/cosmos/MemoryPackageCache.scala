package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.{PackageRepository, SearchResult}
import com.mesosphere.cosmos.repository.Repository
import com.mesosphere.universe._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.util.Future

/** A package cache that stores all package information in memory. Useful for testing.
  *
  * @param packages the contents of the cache: package data indexed by package name
  */
final case class MemoryPackageCache(packages: Map[String, PackageFiles]) extends Repository {

  override def uri: Uri = throw new UnsupportedOperationException()

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles] = {
    Future.value {
      packages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(a) => a
      }
    }
  }

  override def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles] = {
    Future.value {
      packages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(a) => a
      }
    }
  }

  override def search(query: Option[String]): Future[List[SearchResult]] = {
    Future.exception(RepositoryNotFound("http://example.com/universe.zip"))
  }

  override def getPackageIndex(
    packageName: String
  ): Future[UniverseIndexEntry] = {
    Future.exception(PackageNotFound(packageName))
  }
}
