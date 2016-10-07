package com.mesosphere.universe.v3.model

import io.circe.JsonObject
import java.util.regex.Pattern

import com.twitter.util.{Return, Throw, Try}

sealed abstract class PackageDefinition
object PackageDefinition {
  case class Version(override val toString: String) extends AnyVal

  case class Tag(value: String) {
    import Tag._
    assert(                                                    //TODO: move this to companion object with explicit Try
      packageDetailsTagPattern.matcher(value).matches(),
      s"Value '$value' does not conform to expected format $packageDetailsTagRegex"
    )
  }
  object Tag {
    val packageDetailsTagRegex = "^[^\\s]+$"
    val packageDetailsTagPattern = Pattern.compile(packageDetailsTagRegex)
  }

  final class ReleaseVersion private(val value: Int) extends AnyVal

  object ReleaseVersion {

    def apply(value: Int): Try[ReleaseVersion] = {
      if (value >= 0) Return(new ReleaseVersion(value))
      else Throw(new IllegalArgumentException(s"Expected integer value >= 0 for release version, but found [$value]"))
    }

    implicit val packageDefinitionReleaseVersionOrdering: Ordering[ReleaseVersion] = Ordering.by(_.value)

  }

}

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-3.x/repo/meta/schema/v3-repo-schema.json#/definitions/v20Package
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
) extends PackageDefinition

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-3.x/repo/meta/schema/v3-repo-schema.json#/definitions/v30Package
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
) extends PackageDefinition

object V3Package {
  implicit val v3PackageOrdering: Ordering[V3Package] = Ordering.by(p => p.name -> p.releaseVersion)
}
