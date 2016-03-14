package com.mesosphere.cosmos.model

import com.mesosphere.universe.{Resource, PackageDetails, Command}
import io.circe.JsonObject

case class DescribeResponse(
  `package`: PackageDetails,
  marathonMustache: String,
  command: Option[Command] = None,
  config: Option[JsonObject] = None,
  resource: Option[Resource] = None
)
