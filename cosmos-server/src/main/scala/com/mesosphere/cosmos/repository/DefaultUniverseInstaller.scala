package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.universe
import com.twitter.util.Future

final class DefaultUniverseInstaller private(
  packageStorage: PackageStorage
) extends UniverseInstaller {

  private[this] val install = installSupportedPackage(packageStorage) _

  def apply(pkg: universe.v3.model.SupportedPackageDefinition): Future[Unit] = install(pkg)

}

object DefaultUniverseInstaller {

  def apply(
    packageStorage: PackageStorage
  ): DefaultUniverseInstaller = {
    new DefaultUniverseInstaller(packageStorage)
  }

}
