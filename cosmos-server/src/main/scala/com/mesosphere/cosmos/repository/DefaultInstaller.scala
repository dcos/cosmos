package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import java.util.UUID

final class DefaultInstaller private (
  stageObjectStorage: StagedPackageStorage,
  packageObjectStorage: PackageObjectStorage,
  localPackageCollection: LocalPackageCollection
) extends Installer {
  def apply(
    uri: UUID,
    pkg: universe.v3.model.PackageDefinition
  ): Future[Unit] = {
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
