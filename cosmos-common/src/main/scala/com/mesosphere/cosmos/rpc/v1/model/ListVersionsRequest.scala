package com.mesosphere.cosmos.rpc.v1.model

case class ListVersionsRequest(
  packageName: String,
  includePackageVersions: Boolean
)
