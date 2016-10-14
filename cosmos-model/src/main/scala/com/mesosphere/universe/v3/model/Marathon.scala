package com.mesosphere.universe.v3.model

import java.nio.ByteBuffer

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/marathon
  */
case class Marathon(v2AppMustacheTemplate: ByteBuffer)
