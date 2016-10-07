package com.mesosphere.cosmos.internal.model

import com.mesosphere.universe
import com.mesosphere.universe.v3.model._
import io.circe.JsonObject

sealed abstract class BundleDefinition

case class V2Bundle(
  packagingVersion: V2PackagingVersion.type = V2PackagingVersion,
  name: String,
  version: universe.v3.model.PackageDefinition.Version,
  maintainer: String,
  description: String,
  marathon: Marathon,
  tags: List[universe.v3.model.PackageDefinition.Tag] = Nil,
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
) extends BundleDefinition

case class V3Bundle(
  packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
  name: String,
  version: universe.v3.model.PackageDefinition.Version,
  maintainer: String,
  description: String,
  tags: List[universe.v3.model.PackageDefinition.Tag] = Nil,
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
) extends BundleDefinition
