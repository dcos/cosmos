package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.v2.model.PackageDetailsVersion

case class DescribeRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion]
)
