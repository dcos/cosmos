package com.mesosphere.cosmos.model

import com.mesosphere.universe.{Resource, PackageDetails, Command}
import io.circe.JsonObject

case class DescribeResponse(
  `package`: PackageDetails,
  marathonMustache: Option[String] = None,
  command: Option[Command] = None,
  config: Option[JsonObject] = None,
  resource: Option[Resource] = None
)
