package com.mesosphere.cosmos.model

case class ListRequest(
  packageName: Option[String] = None,
  appId: Option[String] = None
)
