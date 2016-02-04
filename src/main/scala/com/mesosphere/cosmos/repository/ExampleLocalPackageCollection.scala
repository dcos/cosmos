package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageCache
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.universe.{ReleaseVersion, PackageDetailsVersion, PackageFiles, UniverseIndexEntry}
import com.netaporter.uri.Uri
import com.twitter.util.Future

/** This wraps PackageCache and could eventually be merged with it.
  *
  * '''EXAMPLE CODE -- DO NOT USE'''
  */
private final class ExampleLocalPackageCollection(
  val name: String,
  val source: Uri,
  repository: PackageCache
) extends PackageCollection with Repository {

  def getPackageByPackageVersion(packageName: String, packageVersion: Option[PackageDetailsVersion]): Future[PackageFiles] = {
    repository.getPackageByPackageVersion(packageName, packageVersion)
  }

  def getPackageByReleaseVersion(packageName: String, releaseVersion: ReleaseVersion): Future[PackageFiles] = {
    repository.getPackageByReleaseVersion(packageName, releaseVersion)
  }

  def getPackageInfo(packageName: String): Future[UniverseIndexEntry] = {
    repository.getPackageIndex(packageName)
  }

  def search(query: Option[String]): Future[List[UniverseIndexEntry]] = {
    repository
      .getRepoIndex
      .map(repoIndex => PackageSearchHandler.search(repoIndex.packages, query))
  }

}
