package com.mesosphere.universe.v3.model

case class Assets(
  uris: Option[Map[String, String]],
  container: Option[Container]
)
