package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe
import io.circe.JsonObject

case class ServiceStartRequest(
  packageName: String,
  packageVersion: Option[universe.v3.model.PackageDefinition.Version] = None,
  options: Option[JsonObject] = None
)
