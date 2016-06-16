package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait CosmosRepository extends PackageCollection {

  def repository: rpc.v1.model.PackageRepository

  def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v2.model.ReleaseVersion
  ): Future[universe.v2.model.PackageFiles]
}

// TODO (version): Rename to CosmosRepository
/** A repository of packages that can be installed on DCOS. */
trait V3CosmosRepository extends V3PackageCollection {

  def repository: rpc.v1.model.PackageRepository

  def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v2.model.ReleaseVersion
  ): Future[internal.model.PackageDefinition]
}

object V3CosmosRepository {
  def apply(repository: rpc.v1.model.PackageRepository,
            universeClient: V3UniverseClient): V3CosmosRepository = {
    new DefaultCosmosRepository(repository, universeClient)
  }
}

final class DefaultCosmosRepository(
    override val repository: rpc.v1.model.PackageRepository,
    universeClient: V3UniverseClient
)
    extends V3CosmosRepository {

  override def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v2.model.ReleaseVersion
  ): Future[internal.model.PackageDefinition] = ???

  override def getPackageByPackageVersion(
      packageName: String,
      packageVersion: Option[universe.v3.model.PackageDefinition.Version])
    : Future[(internal.model.PackageDefinition, Uri)] = ???

  override def getPackagesByPackageName(
      packageName: String): Future[List[internal.model.PackageDefinition]] =
    ???

  override def search(
      query: Option[String]): Future[List[rpc.v1.model.SearchResult]] =
    ???
}
