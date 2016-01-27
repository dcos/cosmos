package com.mesosphere.cosmos

import com.mesosphere.cosmos.model._
import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait PackageCache {

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[String]
  ): Future[PackageFiles]

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: String
  ): Future[PackageFiles]

  def getPackageIndex(packageName: String): Future[PackageInfo]

  def getRepoIndex: Future[UniverseIndex]

}

object PackageCache {

  /** Useful when a cache is not needed or should not be used. */
  object empty extends PackageCache {

    override def getPackageByPackageVersion(
      packageName: String,
      packageVersion: Option[String]
    ): Future[PackageFiles] = {
      Future.exception(PackageNotFound(packageName))
    }

    override def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: String
    ): Future[PackageFiles] = {
      Future.exception(PackageNotFound(packageName))
    }

    def getRepoIndex: Future[UniverseIndex] = {
      Future.exception(RepositoryNotFound())
    }

    def getPackageIndex(packageName: String): Future[PackageInfo] = {
      Future.exception(PackageNotFound(packageName))
    }

  }
}
