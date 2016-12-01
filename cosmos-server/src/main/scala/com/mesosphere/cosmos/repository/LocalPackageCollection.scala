package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.CosmosError
import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.cosmos.search.searchForPackages
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.util.Future

// TODO (devflow) (jsancio): Make sure that we look at the operation store
final class LocalPackageCollection private (objectStorage: PackageObjectStorage) {
  // Used by list
  final def list(): Future[List[rpc.v1.model.LocalPackage]] = {
    objectStorage.list().map(_.sorted.reverse)
  }

  // Used by uninstall and render
  // We need this because the user may not always specify the full PackageCoordinate.
  // They may only specify the name.
  final def getInstalledPackage(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): Future[rpc.v1.model.LocalPackage] = {
    list().map { packages =>
      LocalPackageCollection.installedPackage(packages, packageName, packageVersion)
    }
  }

  // Used by describe
  // We need this in addition to getInstalledPackage because we want to be able to
  // describe any package while we only want to render or uninstall installed packages.
  final def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): Future[rpc.v1.model.LocalPackage] = {
    list().map { packages =>
      LocalPackageCollection.packageByPackageVersion(packages, packageName, packageVersion)
    }
  }

  // Used by listVersion
  final def getPackageByPackageName(
    packageName: String
  ): Future[List[rpc.v1.model.LocalPackage]] = {
    list().map { packages =>
      LocalPackageCollection.packageByPackageName(packages, packageName)
    }
  }

  // Used by search
  final def search(query: Option[String]): Future[List[rpc.v1.model.LocalPackage]] = {
    list().map(packages => searchForPackages(packages, query))
  }
}

object LocalPackageCollection {
  def apply(objectStorage: PackageObjectStorage): LocalPackageCollection = {
    new LocalPackageCollection(objectStorage)
  }

  def installedPackage(
    packages: List[rpc.v1.model.LocalPackage],
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): rpc.v1.model.LocalPackage = {
    val namedPackages = packages
      .filter(pkg => pkg.packageName == packageName)
      .filter(pkg => pkg.isInstanceOf[rpc.v1.model.Installed])

    resolveVersion(namedPackages, packageName, packageVersion)
  }

  def packageByPackageVersion(
    packages: List[rpc.v1.model.LocalPackage],
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): rpc.v1.model.LocalPackage = {
    val namedPackages = packages.filter(pkg => pkg.packageName == packageName)

    resolveVersion(namedPackages, packageName, packageVersion)
  }

  final def packageByPackageName(
    packages: List[rpc.v1.model.LocalPackage],
    packageName: String
  ): List[rpc.v1.model.LocalPackage] = {
    packages.filter(_.packageName == packageName)
  }

  private[this] def resolveVersion(
    packages: List[rpc.v1.model.LocalPackage],
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): rpc.v1.model.LocalPackage = {
    val optionalPackage = packageVersion match {
      case Some(version) => packages.find(_.packageVersion == version)
      case None => packages.headOption
    }

    (packageVersion, packages, optionalPackage) match {
      case (_, _, Some(pkg)) =>
        // Found the installed package
        pkg
      case (Some(version), _ :: _, None) =>
        // Found an Installed package with that name but not with that version
        throw VersionNotFound(packageName, version)
      case _ =>
        // Didn't find the package
        throw PackageNotFound(packageName)
    }
  }
}
