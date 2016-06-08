package com.mesosphere.universe.v3.model

import io.circe.JsonObject

import java.util.regex.Pattern

sealed abstract class PackageDefinition
object PackageDefinition {
  case class Version(override val toString: String) extends AnyVal

  case class Tag(value: String) {
    import Tag._
    assert(
      packageDetailsTagPattern.matcher(value).matches(),
      s"Value '$value' does not conform to expected format $packageDetailsTagRegex"
    )
  }
  object Tag {
    val packageDetailsTagRegex = "^[^\\s]+$"
    val packageDetailsTagPattern = Pattern.compile(packageDetailsTagRegex)
  }

  case class ReleaseVersion(value: Int) {
    assert(value >= 0, s"Value $value is not >= 0")
  }

}

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-3.x/repo/meta/schema/v3-repo-schema.json#/definitions/v20Package
  */
case class V2Package(
  packagingVersion: V2PackagingVersion,
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
  marathon: Option[Marathon] = None,
  resource: Option[Resource] = None,
  config: Option[JsonObject] = None,
  command: Option[Command] = None
) extends PackageDefinition

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-3.x/repo/meta/schema/v3-repo-schema.json#/definitions/v30Package
  */
case class V3Package(
  packagingVersion: V3PackagingVersion,
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
  resource: Option[Resource] = None,
  config: Option[JsonObject] = None,
  command: Option[Command] = None
) extends PackageDefinition
