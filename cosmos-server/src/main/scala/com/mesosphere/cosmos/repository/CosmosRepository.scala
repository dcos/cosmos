package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.model.PackageRepository
import com.mesosphere.universe.v2.model.{PackageFiles, ReleaseVersion}
import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait CosmosRepository extends PackageCollection {

  def repository: PackageRepository

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles]
}
