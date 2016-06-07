package com.mesosphere.cosmos.model

import com.mesosphere.universe.v2.model.{PackageDetailsVersion, ReleaseVersion}

case class ListVersionsResponse(
  results: Map[PackageDetailsVersion, ReleaseVersion]
)
