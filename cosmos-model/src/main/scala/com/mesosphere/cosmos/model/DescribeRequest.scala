package com.mesosphere.cosmos.model

import com.mesosphere.universe.v2.PackageDetailsVersion

case class DescribeRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion]
)
