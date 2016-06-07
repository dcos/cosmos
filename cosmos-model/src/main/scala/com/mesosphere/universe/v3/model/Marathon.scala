package com.mesosphere.universe.v3.model

import java.nio.ByteBuffer

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-3.x/repo/meta/schema/v3-repo-schema.json#/definitions/marathon
  */
case class Marathon(v2AppMustacheTemplate: ByteBuffer)
