package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try

final class PackageAdder private (
  tempObjectStorage: ObjectStorage,
  packageObjectStorage: PackageObjectStorage,
  localPackageCollection: LocalPackageCollection
) {
  def apply(
    uri: Uri,
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

object PackageAdder {
  def apply(
  tempObjectStorage: ObjectStorage,
  packageObjectStorage: PackageObjectStorage,
  localPackageCollection: LocalPackageCollection
  ): PackageAdder = new PackageAdder(
    tempObjectStorage,
    packageObjectStorage,
    localPackageCollection
  )
}
