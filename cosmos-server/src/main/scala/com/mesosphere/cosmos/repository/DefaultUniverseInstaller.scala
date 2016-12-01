package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.universe
import com.twitter.util.Future

final class DefaultUniverseInstaller private(
  packageObjectStorage: PackageObjectStorage,
  localPackageCollection: LocalPackageCollection
) extends UniverseInstaller {

  private[this] val install = installV3Package(localPackageCollection, packageObjectStorage) _

  def apply(pkg: universe.v3.model.V3Package): Future[Unit] = install(pkg)

}

object DefaultUniverseInstaller {

  def apply(
    packageObjectStorage: PackageObjectStorage,
    localPackageCollection: LocalPackageCollection
  ): DefaultUniverseInstaller = {
    new DefaultUniverseInstaller(packageObjectStorage, localPackageCollection)
  }

}
