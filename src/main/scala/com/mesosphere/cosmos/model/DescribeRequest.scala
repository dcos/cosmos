package com.mesosphere.cosmos.model

import io.circe.JsonObject

case class DescribeRequest(
  packageName: String,
  packageVersion: Option[String]
)
case class DescribeResponse(
  `package`: PackageDefinition,
  marathonMustache: String,
  command: Option[CommandDefinition] = None,
  config: Option[JsonObject] = None,
  resource: Option[Resource] = None
)

case class ListVersionsRequest(
  packageName: String,
  packageVersions: Boolean
)
case class ListVersionsResponse(
  // TODO: packageVersion -> revisionVersion
  // example: cassandra: 0.1.0-1 -> 0
  results: Map[String, String]
)
