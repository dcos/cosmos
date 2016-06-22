package com.mesosphere.cosmos.label.v1.model

import com.mesosphere.universe

/** Copy of [[com.mesosphere.universe.v2.model.PackageDetails]] with additional field `images` from
  * [[com.mesosphere.universe.v2.model.Resource]].
  */
case class PackageMetadata(
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
  licenses: Option[List[universe.v2.model.License]] = None,
  images: Option[universe.v2.model.Images] = None
)
