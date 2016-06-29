package com.mesosphere.cosmos.rpc.v2.model

import com.mesosphere.universe
import io.circe.JsonObject

case class DescribeResponse(
  packagingVersion: universe.v3.model.PackagingVersion,
  name: String,
  version: universe.v3.model.PackageDefinition.Version,
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
