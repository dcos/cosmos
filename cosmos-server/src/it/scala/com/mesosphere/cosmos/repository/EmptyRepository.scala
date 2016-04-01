package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.model.{PackageRepository, SearchResult}
import com.mesosphere.cosmos.{PackageNotFound, RepositoryNotFound}
import com.mesosphere.universe.{PackageDetailsVersion, PackageFiles, ReleaseVersion, UniverseIndexEntry}
import com.netaporter.uri.Uri
import com.twitter.util.Future

/** Useful when a repository is not needed or should not be used. */
object EmptyRepository extends Repository {

  override def uri: Uri = throw new UnsupportedOperationException()

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

  override def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
    Future.exception(PackageNotFound(packageName))
  }

  override def search(query: Option[String]): Future[List[SearchResult]] = {
    Future.exception(RepositoryNotFound(Uri.parse("http://example.com/universe.zip")))
  }

}
