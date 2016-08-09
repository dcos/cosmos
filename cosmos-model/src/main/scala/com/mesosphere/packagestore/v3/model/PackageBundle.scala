package com.mesosphere.packagestore.v3.model

import com.mesosphere.universe.v3.model._
import io.circe.JsonObject

sealed abstract class PackageBundle {
  def toPackageDefinition(releaseVersion: PackageDefinition.ReleaseVersion): PackageDefinition
}

case class V2PackageBundle(
  packagingVersion: V2PackagingVersion.type = V2PackagingVersion,
  name: String,
  version: PackageDefinition.Version,
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
) extends PackageBundle {
  override def toPackageDefinition(releaseVersion: PackageDefinition.ReleaseVersion): PackageDefinition =
    V2Package(
      packagingVersion,
      name,
      version,
      releaseVersion,
      maintainer,
      description,
      marathon,
      tags,
      selected,
      scm,
      website,
      framework,
      preInstallNotes,
      postInstallNotes,
      postUninstallNotes,
      licenses,
      resource,
      config,
      command
    )
}

case class V3PackageBundle(
  packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
  name: String,
  version: PackageDefinition.Version,
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
) extends PackageBundle {
  override def toPackageDefinition(releaseVersion: PackageDefinition.ReleaseVersion): PackageDefinition =
    V3Package(
      packagingVersion,
      name,
      version,
      releaseVersion,
      maintainer,
      description,
      tags,
      selected,
      scm,
      website,
      framework,
      preInstallNotes,
      postInstallNotes,
      postUninstallNotes,
      licenses,
      minDcosReleaseVersion,
      marathon,
      resource,
      config,
      command
    )
}