package com.mesosphere.universe

case class Assets(
  uris: Option[Map[String, String]], // GitHub issue #58
  container: Option[Container]
)
