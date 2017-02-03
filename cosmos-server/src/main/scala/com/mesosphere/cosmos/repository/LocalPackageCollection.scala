package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.search.searchForPackages
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.cosmos.storage.installqueue.ReaderView
import com.mesosphere.cosmos.storage
import com.mesosphere.universe
import com.twitter.util.Future

final class LocalPackageCollection private(
  packageStorage: PackageStorage,
  installQueue: ReaderView
) {

  // Used by list
  final def list(): Future[List[rpc.v1.model.LocalPackage]] = {
    for {
      operatingPackages <- installQueue.viewStatus()
      storedPackages <- packageStorage.readAllLocalPackages()
    } yield {
      val pendingLocalPackages = operatingPackages.mapValues(
        LocalPackageCollection.operationStatusToLocalPackage
      )

      // Operation queue takes precedence over object storage in the case of conflict
      val result = storedPackages.map(pkg => pkg.packageCoordinate -> pkg).toMap ++
        pendingLocalPackages

      result.values.toList.sorted.reverse
    }
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
  def apply(
    packageStorage: PackageStorage,
    installQueue: ReaderView
  ): LocalPackageCollection = {
    new LocalPackageCollection(packageStorage, installQueue)
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

  private def operationStatusToLocalPackage(
    status: storage.v1.model.OperationStatus
  ): rpc.v1.model.LocalPackage = status match {
    case storage.v1.model.PendingStatus(storage.v1.model.Install(_, pkg), _) =>
      rpc.v1.model.Installing(pkg)
    case storage.v1.model.PendingStatus(storage.v1.model.UniverseInstall(pkg), _) =>
      rpc.v1.model.Installing(pkg)
    case storage.v1.model.PendingStatus(storage.v1.model.Uninstall(pkg), _) =>
      rpc.v1.model.Uninstalling(Right(pkg))
    case storage.v1.model.FailedStatus(storage.v1.model.OperationFailure(ops, error)) =>
      rpc.v1.model.Failed(ops, error)
  }
}
