package com.mesosphere.cosmos.model

import com.mesosphere.universe.{PackageDetailsVersion, UniverseIndexEntry}

case class SearchResponse(packages: List[UniverseIndexEntry])
case class SearchResponseV2(packages: List[SearchResponseV2Entry])
case class SearchResponseV2Entry(
  name: String,
  currentVersion: PackageDetailsVersion,
  description: String,
  framework: Boolean = false,
  tags: List[String]    //TODO: pattern: "^[^\\s]+$"
  , routes: List[String]
)
