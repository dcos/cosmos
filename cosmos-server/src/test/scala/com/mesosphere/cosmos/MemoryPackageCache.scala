package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.CosmosRepository
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.util.Future

/** A package cache that stores all package information in memory. Useful for testing.
  *
  * @param packages the contents of the cache: package data indexed by package name
  */
final case class MemoryPackageCache(
  packages: Map[String, internal.model.PackageDefinition],
  sourceUri: Uri
) extends CosmosRepository {

  override def repository: rpc.v1.model.PackageRepository = {
    throw new UnsupportedOperationException()
  }

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  )(implicit session: RequestSession): Future[(internal.model.PackageDefinition, Uri)] = {
    Future.value {
      packages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(a) => (a, sourceUri)
      }
    }
  }

  override def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion
  )(implicit session: RequestSession): Future[internal.model.PackageDefinition] = {
    Future.value {
      packages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(a) => a
      }
    }
  }

  override def search(query: Option[String])(implicit session: RequestSession): Future[List[rpc.v1.model.SearchResult]] = {
    Future.exception(RepositoryNotFound("http://example.com/universe.zip"))
  }

  override def getPackagesByPackageName(
    packageName: String
  )(implicit session: RequestSession): Future[List[internal.model.PackageDefinition]] = {
    Future.exception(PackageNotFound(packageName))
  }

}
