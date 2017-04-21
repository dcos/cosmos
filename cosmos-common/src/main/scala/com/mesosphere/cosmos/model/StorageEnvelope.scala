package com.mesosphere.cosmos.model

import java.nio.ByteBuffer

case class StorageEnvelope(metadata: Map[String, String], data: ByteBuffer)
