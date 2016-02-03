package com.mesosphere.cosmos.model

import io.circe.JsonObject

case class InstallRequest(
  packageName: String,
  packageVersion: Option[String] = None,
  options: Option[JsonObject] = None,
  appId: Option[AppId] = None
)

case class InstallResponse(
  packageName: String,
  packageVersion: String,
  appId: AppId
)

case class UninstallRequest(
  packageName: String,
  appId: Option[AppId],
  all: Option[Boolean]
)

case class UninstallResponse(results: List[UninstallResult])
case class UninstallResult(
  packageName: String,
  appId: AppId,
  version: Option[String],
  postUninstallNotes: Option[String]
)
