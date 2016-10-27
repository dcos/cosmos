package com.mesosphere.universe.v3.model

import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import io.circe.JsonObject
import java.util.regex.Pattern

sealed abstract class PackageDefinition
object PackageDefinition {
  final case class Version(override val toString: String) extends AnyVal

  final class Tag private(val value: String) extends AnyVal {
    override def toString: String = value
  }
  object Tag {
    val packageDetailsTagRegex = "^[^\\s]+$"
    val packageDetailsTagPattern = Pattern.compile(packageDetailsTagRegex)

    def apply(s: String): Try[Tag] = {
      if (packageDetailsTagPattern.matcher(s).matches()) {
        Return(new Tag(s))
      } else {
        Throw(new IllegalArgumentException(s"Value '$s' does not conform to expected format $packageDetailsTagRegex"))
      }
    }
  }

  final class ReleaseVersion private(val value: Int) extends AnyVal

  object ReleaseVersion {

    def apply(value: Int): Try[ReleaseVersion] = {
      if (value >= 0) {
        Return(new ReleaseVersion(value))
      } else {
        val message = s"Expected integer value >= 0 for release version, but found [$value]"
        Throw(new IllegalArgumentException(message))
      }
    }

    implicit val packageDefinitionReleaseVersionOrdering: Ordering[ReleaseVersion] = Ordering.by(_.value)

  }

  implicit val packageDefinitionOrdering = new Ordering[PackageDefinition] {
    override def compare(a: PackageDefinition, b: PackageDefinition): Int = {
      PackageDefinition.compare(
        (a.name, a.version, a.releaseVersion),
        (b.name, b.version, b.releaseVersion)
      )
    }
  }

  def compare(
    a: (String, Version, ReleaseVersion),
    b: (String, Version, ReleaseVersion)
  ): Int = {
    val (aName, aVersion, aReleaseVersion) = a
    val (bName, bVersion, bReleaseVersion) = b

    val orderName = aName.compare(bName)
    if (orderName != 0) {
      orderName
    } else {
      (SemVer(aVersion.toString), SemVer(bVersion.toString)) match {
        case (Some(_), None) =>
          // semver is greater than non-semver
          1
        case (None, Some(_)) =>
          // semver is greater than non-semver
          -1
        case (Some(aSemver), Some(bSemver)) =>
          // compare semver
          aSemver.compare(bSemver)
        case _ =>
          // both are non-semver; use release version
          aReleaseVersion.value.compare(bReleaseVersion.value)
      }
    }
  }
}

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v20Package
  */
case class V2Package(
  packagingVersion: V2PackagingVersion.type = V2PackagingVersion,
  name: String,
  version: PackageDefinition.Version,
  releaseVersion: PackageDefinition.ReleaseVersion,
  maintainer: String,
  description: String,
  marathon: Marathon,
  tags: List[PackageDefinition.Tag] = Nil,
  selected: Option[Boolean] = None,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[License]] = None,
  resource: Option[V2Resource] = None,
  config: Option[JsonObject] = None,
  command: Option[Command] = None
) extends PackageDefinition with Ordered[V2Package] {
  override def compare(that: V2Package): Int = {
    PackageDefinition.compare(
      (name, version, releaseVersion),
      (that.name, that.version, that.releaseVersion)
    )
  }
}

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v30Package
  */
case class V3Package(
  packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
  name: String,
  version: PackageDefinition.Version,
  releaseVersion: PackageDefinition.ReleaseVersion,
  maintainer: String,
  description: String,
  tags: List[PackageDefinition.Tag] = Nil,
  selected: Option[Boolean] = None,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[License]] = None,
  minDcosReleaseVersion: Option[DcosReleaseVersion] = None,
  marathon: Option[Marathon] = None,
  resource: Option[V3Resource] = None,
  config: Option[JsonObject] = None,
  command: Option[Command] = None
) extends PackageDefinition with Ordered[V3Package] {
  override def compare(that: V3Package): Int = {
    PackageDefinition.compare(
      (name, version, releaseVersion),
      (that.name, that.version, that.releaseVersion)
    )
  }
}
