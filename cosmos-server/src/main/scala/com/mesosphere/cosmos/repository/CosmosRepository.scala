package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.universe.v2.model.{PackageFiles, ReleaseVersion}
import com.mesosphere.universe.v3.model.V3Package

import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait CosmosRepository extends PackageCollection {

  def repository: PackageRepository

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles]
}


// TODO (version): Rename to CosmosRepository
/** A repository of packages that can be installed on DCOS. */
trait V3CosmosRepository extends PackageCollection {

  def repository: PackageRepository

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[V3Package]
}
