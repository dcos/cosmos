package com.mesosphere.cosmos.model

import io.circe.JsonObject

case class RenderRequest(
  packageName: String,
  packageVersion: Option[String] = None,
  options: Option[JsonObject] = None,
  appId: Option[AppId] = None
)
