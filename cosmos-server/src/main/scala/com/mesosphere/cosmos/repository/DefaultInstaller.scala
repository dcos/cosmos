package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.universe
import com.twitter.util.Future
import java.util.UUID

final class DefaultInstaller private (
  stageObjectStorage: StagedPackageStorage,
  packageObjectStorage: PackageObjectStorage,
  localPackageCollection: LocalPackageCollection
) extends Installer {

  private[this] val install = installV3Package(localPackageCollection, packageObjectStorage) _

  def apply(uri: UUID, pkg: universe.v3.model.V3Package): Future[Unit] = install(pkg)

}

object DefaultInstaller {
  def apply(
    stageObjectStorage: StagedPackageStorage,
    packageObjectStorage: PackageObjectStorage,
    localPackageCollection: LocalPackageCollection
  ): DefaultInstaller = new DefaultInstaller(
    stageObjectStorage,
    packageObjectStorage,
    localPackageCollection
  )
}
