package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.storage.v1.model.Operation
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._

sealed trait LocalPackage

object LocalPackage {

  implicit class LocalPackageOps(val value: LocalPackage) extends AnyVal {
    def packageCoordinate: PackageCoordinate = value match {
      case Invalid(_, pc) => pc
      case NotInstalled(pkg) => pkg.packageCoordinate
      case Installed(pkg) => pkg.packageCoordinate
      case Installing(pkg) => pkg.packageCoordinate
      case Uninstalling(Left(pc)) => pc
      case Uninstalling(Right(pkg)) => pkg.packageCoordinate
      case Failed(op, _) => op.packageDefinition.packageCoordinate
    }

    def metadata: Either[PackageCoordinate, universe.v3.model.SupportedPackageDefinition] = value match {
      case Invalid(_, pc) => Left(pc)
      case NotInstalled(pkg) => Right(pkg)
      case Installed(pkg) => Right(pkg)
      case Installing(pkg) => Right(pkg)
      case Uninstalling(data) => data
      case Failed(op, _) => Right(op.packageDefinition)
    }

    def packageName: String = value match {
      case NotInstalled(pkg) => pkg.name
      case Installed(pkg) => pkg.name
      case Installing(pkg) => pkg.name
      case Uninstalling(data) => data.fold(_.name, _.name)
      case Failed(op, _) => op.packageDefinition.name
      case Invalid(_, packageCoordinate) => packageCoordinate.name
    }

    def packageVersion: universe.v3.model.Version = value match {
      case NotInstalled(pkg) => pkg.version
      case Installed(pkg) => pkg.version
      case Installing(pkg) => pkg.version
      case Uninstalling(data) => data.fold(_.version, _.version)
      case Failed(op, _) => op.packageDefinition.version
      case Invalid(_, packageCoordinate) => packageCoordinate.version
    }

    def error: Option[ErrorResponse] = value match {
      case Failed(_, error) => Some(error)
      case Invalid(error, _) => Some(error)
      case _ => None
    }

    def operation: Option[Operation] = value match {
      case Failed(operation, _) => Some(operation)
      case _ => None
    }

  }

  implicit val localPackageOrdering = new Ordering[LocalPackage] {
    override def compare(a: LocalPackage, b: LocalPackage): Int = {
      (a.metadata, b.metadata) match {
        case (Right(aMetadata), Right(bMetadata)) =>
          implicitly[Ordering[universe.v3.model.SupportedPackageDefinition]].compare(aMetadata, bMetadata)
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
    metadata: universe.v3.model.SupportedPackageDefinition,
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
  metadata: universe.v3.model.SupportedPackageDefinition
) extends LocalPackage

final case class Installing(
  metadata: universe.v3.model.SupportedPackageDefinition
) extends LocalPackage

final case class Installed(
  metadata: universe.v3.model.SupportedPackageDefinition
) extends LocalPackage

final case class Uninstalling(
  data: Either[PackageCoordinate, universe.v3.model.SupportedPackageDefinition]
) extends LocalPackage

// TODO: We shouldn't return Operation. It contains stagedPackageId which is useless to the client
final case class Failed(
  operation: Operation,
  error: ErrorResponse
) extends LocalPackage

final case class Invalid(
  error: ErrorResponse,
  packageCoordinate: PackageCoordinate
) extends LocalPackage
