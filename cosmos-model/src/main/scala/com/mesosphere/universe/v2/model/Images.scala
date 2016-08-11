package com.mesosphere.universe.v2.model

case class Images(
  iconSmall: Option[String],
  iconMedium: Option[String],
  iconLarge: Option[String],
  screenshots: Option[List[String]]
)
