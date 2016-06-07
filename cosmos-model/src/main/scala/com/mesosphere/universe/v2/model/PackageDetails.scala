package com.mesosphere.universe.v2.model

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/package-schema.json
  */
case class PackageDetails(
  packagingVersion: PackagingVersion,
  name: String,
  version: PackageDetailsVersion,
  maintainer: String,
  description: String,
  tags: List[String] = Nil,         //TODO: pattern: "^[^\\s]+$"
  selected: Option[Boolean] = None,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[License]] = None
)
