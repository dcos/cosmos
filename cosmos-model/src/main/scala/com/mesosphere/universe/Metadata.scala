package com.mesosphere.universe

import io.circe.JsonObject

package v3.model {

  sealed trait Metadata

  case class V3Metadata(
    packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
    name: String,
    version: Version,
    maintainer: String,
    description: String,
    tags: List[Tag] = Nil,
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
    config: Option[JsonObject] = None
  ) extends Metadata

}
