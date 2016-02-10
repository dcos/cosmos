package com.mesosphere.cosmos.repository

import com.netaporter.uri.dsl.stringToUri
import com.twitter.util.Future

import com.mesosphere.universe._
import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.RepositoryNotFound

/** A repository of packages that can be installed on DCOS. */
trait Repository extends PackageCollection {

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles]

  // TODO XXX (jsancio): We need to remove this!!
  def getRepoIndex: Future[UniverseIndex]

}

object Repository {

  /** Useful when a cache is not needed or should not be used. */
  object empty extends Repository {

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

    override def getRepoIndex: Future[UniverseIndex] = {
      Future.exception(RepositoryNotFound("http://example.com/universe.zip"))
    }

    override def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
      Future.exception(PackageNotFound(packageName))
    }
  }
}
