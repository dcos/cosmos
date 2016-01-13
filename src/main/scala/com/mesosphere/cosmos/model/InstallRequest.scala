package com.mesosphere.cosmos.model

import io.circe.JsonObject

case class InstallRequest(
  name: String,
  version: Option[String] = None,
  options: JsonObject = JsonObject.empty,
  appId: Option[String] = None
)
case class UninstallRequest(
  name: String,
  appId: Option[String],
  all: Option[Boolean]
)

case class UninstallResponse(results: List[UninstallResult])
case class UninstallResult(
  name: String,
  appId: String,
  version: Option[String]
)
