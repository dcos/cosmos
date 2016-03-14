package com.mesosphere.cosmos.model

case class ListVersionsRequest(
  packageName: String,
  includePackageVersions: Boolean
)
