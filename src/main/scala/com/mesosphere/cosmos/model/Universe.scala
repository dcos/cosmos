package com.mesosphere.cosmos.model

case class License(name: String, url: String)

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/package-schema.json
  */
case class PackageDefinition(
  packagingVersion: String,
  name: String,
  version: String,
  maintainer: String,
  description: String,
  tags: List[String] = Nil,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[License]] = None
)

case class Container(
  docker: Map[String, String]
)
object Container {
  val empty = Container(Map.empty)
}

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/command-schema.json
  */
case class CommandDefinition(pip: List[String])

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

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/resource-schema.json
  */
case class Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None
)

// index.json schema for each package from Universe
case class UniverseIndexEntry(
  name: String,
  currentVersion: String,
  versions: Map[String, String], // software versions -> package revision
  description: String,
  framework: Boolean = false,
  tags: List[String]
)

// index.json schema from Universe
/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/index-schema.json
  */
case class UniverseIndex(
  version: String,
  packages: List[UniverseIndexEntry]
) {

  def getPackages: Map[String, PackageInfo] = {
    packages.map { packageInfo =>
      (packageInfo.name,
        PackageInfo(
          packageInfo.currentVersion,
          packageInfo.versions,
          packageInfo.description,
          packageInfo.framework,
          packageInfo.tags
        )
      )
    }.toMap
  }
}

case class PackageInfo(
  currentVersion: String,
  versions: Map[String, String], // version -> revision
  description: String,
  framework: Boolean = false,
  tags: List[String]
)

