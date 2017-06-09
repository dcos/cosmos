package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import java.util.UUID

package object repository {
  type Installer = (UUID, universe.v4.model.SupportedPackageDefinition) => Future[Unit]

  type UniverseInstaller = universe.v4.model.SupportedPackageDefinition => Future[Unit]
  object UniverseInstaller {
    object Noop extends UniverseInstaller {
      def apply(p: universe.v4.model.SupportedPackageDefinition): Future[Unit] = Future.Done
    }
  }

  type Uninstaller = universe.v4.model.SupportedPackageDefinition => Future[Unit]
  object Uninstaller {
    object Noop extends Uninstaller {
      def apply(p: universe.v4.model.SupportedPackageDefinition): Future[Unit] = Future.Done
    }
  }

  // TODO package-add: Need tests for error cases
  def installSupportedPackage(
    packageStorage: PackageStorage
  )(
    pkg: universe.v4.model.SupportedPackageDefinition
  ): Future[Unit] = {

    packageStorage.readAllLocalPackages().map { packages =>
      LocalPackageCollection.installedPackage(
        packages,
        pkg.name,
        Some(pkg.version)
      )
    }.transform {
      case Throw(CosmosException(VersionNotFound(_, _), _, _, _)) |
      Throw(CosmosException(PackageNotFound(_), _, _, _)) =>
        // Put the PackageDefinition in the package object storage.
        packageStorage.writePackageDefinition(pkg)
      case Throw(error) =>
        Future.exception(error)
      case Return(_) =>
        // Package already installed: noop.
        Future.Done
    }
  }

}
