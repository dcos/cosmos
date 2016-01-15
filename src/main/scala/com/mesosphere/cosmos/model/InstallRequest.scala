package com.mesosphere.cosmos.model

import io.circe.JsonObject

case class InstallRequest(
  name: String,
  version: Option[String] = None,
  options: JsonObject = JsonObject.empty
)
