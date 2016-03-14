package com.mesosphere.universe

case class Images(
  iconSmall: String,
  iconMedium: String,
  iconLarge: String,
  screenshots: Option[List[String]]
)
