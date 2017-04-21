package com.mesosphere.universe.v2.model

// index.json schema for each package from Universe
case class UniverseIndexEntry(
  name: String,
  currentVersion: PackageDetailsVersion,
  versions: Map[PackageDetailsVersion, ReleaseVersion],
  description: String,
  framework: Boolean = false,
  tags: List[String],
  selected: Option[Boolean] = None
)
