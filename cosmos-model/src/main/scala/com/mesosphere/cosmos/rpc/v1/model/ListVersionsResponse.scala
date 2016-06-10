package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.v2.model.{PackageDetailsVersion, ReleaseVersion}

case class ListVersionsResponse(
  results: Map[PackageDetailsVersion, ReleaseVersion]
)
