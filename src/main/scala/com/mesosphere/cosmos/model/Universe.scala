package com.mesosphere.cosmos.model

case class License(name: String, url: String)

case class PackageDefinition(
  packagingVersion: Option[String],
  name: String,
  version: String,
  maintainer: String,
  description: String,
  tags: List[String],
  scm: Option[String],
  website: Option[String],
  framework: Option[Boolean],
  preInstallNotes: Option[String],
  postInstallNotes: Option[String],
  postUninstallNotes: Option[String],
  licenses: Option[List[License]],
  // This is only here to support adding images to the DCOS_PACKAGE_METADATA Marathon label
  // GitHub issue #57 will decide whether to keep this here
  images: Option[Images]
)

case class Container(
  docker: Map[String, String]
)
object Container {
  val empty = Container(Map.empty)
}

case class Assets(
  uris: Option[Map[String, String]], // GitHub issue #58
  container: Option[Container]
)
object Assets {
  val empty = Assets(None, None)
}

case class Images(
  `icon-small`: String,
  `icon-medium`: String,
  `icon-large`: String,
  screenshots: Option[List[String]]
)

case class Resource(
  assets: Option[Assets],
  images: Option[Images]
)
