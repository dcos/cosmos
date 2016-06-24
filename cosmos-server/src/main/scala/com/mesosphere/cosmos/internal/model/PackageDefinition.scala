package com.mesosphere.cosmos.internal.model

import com.mesosphere.universe
import io.circe.JsonObject

case class PackageDefinition(
    packagingVersion: universe.v3.model.PackagingVersion,
    name: String,
    version: universe.v3.model.PackageDefinition.Version,
    releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion,
    maintainer: String,
    description: String,
    tags: List[universe.v3.model.PackageDefinition.Tag] = Nil,
    selected: Boolean = false,
    scm: Option[String] = None,
    website: Option[String] = None,
    framework: Boolean = false,
    preInstallNotes: Option[String] = None,
    postInstallNotes: Option[String] = None,
    postUninstallNotes: Option[String] = None,
    licenses: Option[List[universe.v3.model.License]] = None,
    minDcosReleaseVersion: Option[universe.v3.model.DcosReleaseVersion] = None,
    marathon: Option[universe.v3.model.Marathon] = None,
    resource: Option[universe.v3.model.V3Resource] = None,
    config: Option[JsonObject] = None,
    command: Option[universe.v3.model.Command] = None
)

object PackageDefinition {
  implicit val ordering: Ordering[PackageDefinition] = Ordering.by(p => p.name -> p.releaseVersion)
}
