package com.mesosphere.cosmos.model

import com.mesosphere.universe.v2.{PackageDetailsVersion, ReleaseVersion}

case class ListVersionsResponse(
  results: Map[PackageDetailsVersion, ReleaseVersion]
)
