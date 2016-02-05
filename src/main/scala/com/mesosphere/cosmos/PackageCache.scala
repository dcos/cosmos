package com.mesosphere.cosmos

import com.netaporter.uri.dsl.stringToUri
import com.mesosphere.universe._
import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait PackageCache {

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles]

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles]

  def getPackageIndex(packageName: String): Future[UniverseIndexEntry]

  def getRepoIndex: Future[UniverseIndex]

}

object PackageCache {

  /** Useful when a cache is not needed or should not be used. */
  object empty extends PackageCache {

    override def getPackageByPackageVersion(
      packageName: String,
      packageVersion: Option[PackageDetailsVersion]
    ): Future[PackageFiles] = {
      Future.exception(PackageNotFound(packageName))
    }

    override def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: ReleaseVersion
    ): Future[PackageFiles] = {
      Future.exception(PackageNotFound(packageName))
    }

    def getRepoIndex: Future[UniverseIndex] = {
      Future.exception(RepositoryNotFound("http://example.com/universe.zip"))
    }

    def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
      Future.exception(PackageNotFound(packageName))
    }

  }
}
