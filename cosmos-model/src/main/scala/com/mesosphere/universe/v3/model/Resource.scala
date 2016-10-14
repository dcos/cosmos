package com.mesosphere.universe.v3.model

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v20resource
  */
case class V2Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None
)

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v30resource
  */
case class V3Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None,
  cli: Option[Cli] = None
)
