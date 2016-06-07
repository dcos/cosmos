package com.mesosphere.cosmos.model

import com.mesosphere.universe.v2.model.{Images, PackageDetailsVersion, ReleaseVersion}

case class SearchResult(
  name: String,
  currentVersion: PackageDetailsVersion,
  versions: Map[PackageDetailsVersion, ReleaseVersion],
  description: String,
  framework: Boolean = false,
  tags: List[String],    //TODO: pattern: "^[^\\s]+$",
  selected: Option[Boolean] = None,
  images: Option[Images] = None
)
