package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.CosmosError
import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.cosmos.search.searchForPackages
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.util.Future

trait LocalPackageCollection {
  // Used by list
  def list(): Future[List[rpc.v1.model.LocalPackage]]

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
      packages.filter(_.packageName == packageName)
    }
  }

  // Used by search
  final def search(query: Option[String]): Future[List[rpc.v1.model.LocalPackage]] = {
    list().map(packages => searchForPackages(packages, query))
  }
}

object LocalPackageCollection {

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
      case (Some(version), _ :: _, None) =>
        // Found an Installed package with that name but not with that version
        throw VersionNotFound(packageName, version)
      case (_, _, Some(pkg)) =>
        // Found the installed package
        pkg
      case _ =>
        // Didn't find the package
        throw PackageNotFound(packageName)
    }
  }
}
