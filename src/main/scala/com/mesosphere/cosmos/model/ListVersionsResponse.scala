package com.mesosphere.cosmos.model

import com.mesosphere.universe.{ReleaseVersion, PackageDetailsVersion}

case class ListVersionsResponse(
  results: Map[PackageDetailsVersion, ReleaseVersion]
)
