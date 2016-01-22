package com.mesosphere.cosmos.model

case class DescribeRequest(
  packageName: String,
  packageVersion: Option[String] = None,
  packageVersions: Option[Boolean] = None
)
