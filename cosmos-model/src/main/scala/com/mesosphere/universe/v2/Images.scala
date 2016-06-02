package com.mesosphere.universe.v2

case class Images(
  iconSmall: String,
  iconMedium: String,
  iconLarge: String,
  screenshots: Option[List[String]]
)
