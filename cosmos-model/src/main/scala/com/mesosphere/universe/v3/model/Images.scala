package com.mesosphere.universe.v3.model

case class Images(
  iconSmall: String,
  iconMedium: String,
  iconLarge: String,
  screenshots: Option[List[String]]
)
