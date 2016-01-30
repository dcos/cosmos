package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageCache
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.model.{PackageFiles, PackageInfo, UniverseIndexEntry}
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

  def getPackageByPackageVersion(packageName: String, packageVersion: Option[String]): Future[PackageFiles] = {
    repository.getPackageByPackageVersion(packageName, packageVersion)
  }

  def getPackageByReleaseVersion(packageName: String, releaseVersion: String): Future[PackageFiles] = {
    repository.getPackageByReleaseVersion(packageName, releaseVersion)
  }

  def getPackageInfo(packageName: String): Future[PackageInfo] = {
    repository.getPackageIndex(packageName)
  }

  def search(query: Option[String]): Future[List[UniverseIndexEntry]] = {
    repository
      .getRepoIndex
      .map(repoIndex => PackageSearchHandler.search(repoIndex.packages, query))
  }

}
