package com.mesosphere.cosmos

import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import java.util.UUID

package object repository {
  type Installer = (UUID, universe.v3.model.V3Package) => Future[Unit]

  type UniverseInstaller = universe.v3.model.V3Package => Future[Unit]
  object UniverseInstaller {
    object Noop extends UniverseInstaller {
      def apply(p: universe.v3.model.V3Package): Future[Unit] = Future.Done
    }
  }

  type Uninstaller = (
    rpc.v1.model.PackageCoordinate,
    Option[universe.v3.model.V3Package]
  ) => Future[Unit]
  object Uninstaller {
    object Noop extends Uninstaller {
      def apply(
        pc: rpc.v1.model.PackageCoordinate,
        p: Option[universe.v3.model.V3Package]
      ): Future[Unit] = Future.Done
    }
  }

  // TODO package-add: Need tests for error cases
  def installV3Package(
    localPackageCollection: LocalPackageCollection,
    packageObjectStorage: PackageObjectStorage
  )(pkg: universe.v3.model.V3Package): Future[Unit] = {
    val packageCoordinate = pkg.packageCoordinate
    localPackageCollection.getInstalledPackage(
      packageCoordinate.name,
      Some(packageCoordinate.version)
    ).transform {
      case Throw(VersionNotFound(_, _)) | Throw(PackageNotFound(_)) =>
        // Put the PackageDefinition in the package object storage.
        packageObjectStorage.writePackageDefinition(pkg)
      case Throw(error) =>
        Future.exception(error)
      case Return(_) =>
        // Package already installed: noop.
        Future.Done
    }
  }

}
