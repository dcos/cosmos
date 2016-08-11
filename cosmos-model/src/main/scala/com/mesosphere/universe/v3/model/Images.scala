package com.mesosphere.universe.v3.model

case class Images(
  iconSmall: Option[String],
  iconMedium: Option[String],
  iconLarge: Option[String],
  screenshots: Option[List[String]]
)
