package com.mesosphere.universe.v3.model

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-3.x/repo/meta/schema/v3-repo-schema.json#/definitions/resource
  */
case class V2Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None
)

case class V3Resource(
  assets: Option[Assets] = None,
  images: Option[Images] = None,
  cli: Option[Cli] = None
)
