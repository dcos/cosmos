package com.mesosphere.cosmos.model

case class UninstallRequest(
  packageName: String,
  appId: Option[AppId],
  all: Option[Boolean]
)
