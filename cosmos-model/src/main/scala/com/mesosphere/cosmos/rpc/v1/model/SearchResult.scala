package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.v2.model.{Images, PackageDetailsVersion, ReleaseVersion}

case class SearchResult(
  name: String,
  currentVersion: PackageDetailsVersion,
  versions: Map[PackageDetailsVersion, ReleaseVersion],
  description: String,
  framework: Boolean = false,
  tags: List[String],
  selected: Option[Boolean] = None,
  images: Option[Images] = None
)
