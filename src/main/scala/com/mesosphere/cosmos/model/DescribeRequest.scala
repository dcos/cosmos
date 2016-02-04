package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion

case class DescribeRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion]
)
