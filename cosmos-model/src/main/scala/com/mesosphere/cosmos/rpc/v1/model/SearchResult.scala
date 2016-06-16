package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe

case class SearchResult(
  name: String,
  currentVersion: universe.v2.model.PackageDetailsVersion,
  versions: Map[universe.v2.model.PackageDetailsVersion, universe.v2.model.ReleaseVersion],
  description: String,
  framework: Boolean = false,
  tags: List[String],
  selected: Option[Boolean] = None,
  images: Option[universe.v2.model.Images] = None
)

// TODO(version): Once we get rid of UniverserPackageCache we should be abel to use this model
case class V3SearchResult(
  name: String,
  currentVersion: universe.v3.model.PackageDefinition.Version,
  versions: Map[universe.v3.model.PackageDefinition.Version, universe.v3.model.PackageDefinition.ReleaseVersion],
  description: String,
  framework: Boolean,
  tags: List[universe.v3.model.PackageDefinition.Tag],
  selected: Option[Boolean] = None,
  images: Option[universe.v3.model.Images] = None
)
