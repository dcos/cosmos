package com.mesosphere.universe

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/resource-schema.json
  */
case class Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None,
  cli: Option[Cli] = None
)
