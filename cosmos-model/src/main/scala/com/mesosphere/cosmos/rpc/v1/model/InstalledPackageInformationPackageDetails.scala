package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe

case class InstalledPackageInformationPackageDetails(
  packagingVersion: universe.v2.model.PackagingVersion,
  name: String,
  version: universe.v2.model.PackageDetailsVersion,
  maintainer: String,
  description: String,
  tags: List[String] = Nil,
  selected: Option[Boolean] = None,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[universe.v2.model.License]] = None
)
