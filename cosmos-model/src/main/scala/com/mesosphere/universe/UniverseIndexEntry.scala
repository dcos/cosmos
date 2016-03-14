package com.mesosphere.universe

// index.json schema for each package from Universe
case class UniverseIndexEntry(
  name: String,
  currentVersion: PackageDetailsVersion,
  versions: Map[PackageDetailsVersion, ReleaseVersion],
  description: String,
  framework: Boolean = false,
  tags: List[String]    //TODO: pattern: "^[^\\s]+$"
)
