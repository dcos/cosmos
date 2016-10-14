package com.mesosphere.cosmos.rpc.v1

import com.mesosphere.cosmos.rpc
import com.mesosphere.universe

package object syntax {
  implicit class LocalPackageOps(val value: rpc.v1.model.LocalPackage) extends AnyVal {
    def pkg: Option[rpc.v2.model.DescribeResponse] = value match {
      case _: rpc.v1.model.Invalid =>
        None
      case rpc.v1.model.NotInstalled(pkg) =>
        Some(pkg)
      case rpc.v1.model.Installed(pkg) =>
        Some(pkg)
      case rpc.v1.model.Installing(pkg) =>
        Some(pkg)
      case rpc.v1.model.Uninstalling(pkg) =>
        Some(pkg)
      case rpc.v1.model.Failed(_, _, pkg) =>
        Some(pkg)
    }

    def packageName: String = value match {
      case rpc.v1.model.NotInstalled(pkg) =>
        pkg.name
      case rpc.v1.model.Installed(pkg) =>
        pkg.name
      case rpc.v1.model.Installing(pkg) =>
        pkg.name
      case rpc.v1.model.Uninstalling(pkg) =>
        pkg.name
      case rpc.v1.model.Failed(_, _, pkg) =>
        pkg.name
      case rpc.v1.model.Invalid(_, packageCoordinate) =>
        packageCoordinate.name
    }

    def packageVersion: universe.v3.model.PackageDefinition.Version = value match {
      case rpc.v1.model.NotInstalled(pkg) =>
        pkg.version
      case rpc.v1.model.Installed(pkg) =>
        pkg.version
      case rpc.v1.model.Installing(pkg) =>
        pkg.version
      case rpc.v1.model.Uninstalling(pkg) =>
        pkg.version
      case rpc.v1.model.Failed(_, _, pkg) =>
        pkg.version
      case rpc.v1.model.Invalid(_, packageCoordinate) =>
        packageCoordinate.version
    }
  }
}
