package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import scala.util.Either
import scala.util.Left
import scala.util.Right

sealed trait LocalPackage
object LocalPackage {
  implicit class LocalPackageOps(val value: LocalPackage) extends AnyVal {
    def metadata: Either[PackageCoordinate, universe.v3.model.PackageDefinition] = value match {
      case Invalid(_, pc) =>
        Left(pc)
      case NotInstalled(pkg) =>
        Right(pkg)
      case Installed(pkg) =>
        Right(pkg)
      case Installing(pkg) =>
        Right(pkg)
      case Uninstalling(pkg) =>
        Right(pkg)
      case Failed(_, _, pkg) =>
        Right(pkg)
    }

    def packageName: String = value match {
      case NotInstalled(pkg) =>
        pkg.name
      case Installed(pkg) =>
        pkg.name
      case Installing(pkg) =>
        pkg.name
      case Uninstalling(pkg) =>
        pkg.name
      case Failed(_, _, pkg) =>
        pkg.name
      case Invalid(_, packageCoordinate) =>
        packageCoordinate.name
    }

    def packageVersion: universe.v3.model.PackageDefinition.Version = value match {
      case NotInstalled(pkg) =>
        pkg.version
      case Installed(pkg) =>
        pkg.version
      case Installing(pkg) =>
        pkg.version
      case Uninstalling(pkg) =>
        pkg.version
      case Failed(_, _, pkg) =>
        pkg.version
      case Invalid(_, packageCoordinate) =>
        packageCoordinate.version
    }
  }

  implicit object localPackageOrdering extends Ordering[LocalPackage] {
    override def compare(a: LocalPackage, b: LocalPackage): Int = {
      (a.metadata, b.metadata) match {
        case (Right(aMetadata), Right(bMetadata)) =>
          implicitly[Ordering[universe.v3.model.PackageDefinition]].compare(aMetadata, bMetadata)
        case (Right(aMetadata), Left(bPackageCoordinate)) =>
          LocalPackage.compare(aMetadata, bPackageCoordinate)
        case (Left(aPackageCoordinate), Right(bMetadata)) =>
          -LocalPackage.compare(bMetadata, aPackageCoordinate)
        case (Left(aPackageCoordinate), Left(bPackageCoordinate)) =>
          LocalPackage.compare(aPackageCoordinate, bPackageCoordinate)
      }
    }
  }

  def compare(
    metadata: universe.v3.model.PackageDefinition,
    packageCoordinate: PackageCoordinate
  ): Int = {
    val nameOrder = metadata.name.compare(packageCoordinate.name)
    if (nameOrder != 0) {
      nameOrder
    } else {
      1
    }
  }

  def compare(a: PackageCoordinate, b: PackageCoordinate): Int = {
    val nameOrder = a.name.compare(b.name)
    if (nameOrder != 0) {
      nameOrder
    } else {
      (
        universe.v3.model.SemVer(a.version.toString),
        universe.v3.model.SemVer(b.version.toString)
      ) match {
        case (Some(aSemVer), Some(bSemVer)) => aSemVer.compare(bSemVer)
        case (Some(_), None) => 1
        case (None, Some(_)) => -1
        case _ => 0
      }
    }
  }
}

final case class NotInstalled(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Installing(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Installed(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Uninstalling(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Failed(
  operation: String, // TODO: Change this when we merge the PackageOps PR
  error: ErrorResponse,
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Invalid(
  error: ErrorResponse,
  packageCoordinate: PackageCoordinate
) extends LocalPackage

/* TODO: Talk about the serialized form
 * {
 *   "status": "not-installed",
 *   "pkg": ...,
 *   "operation": ...,
 *   "error": ...,
 *   "packageCoordinate": ...
 * }
 */
