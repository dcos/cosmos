package com.mesosphere.cosmos.model

import java.nio.ByteBuffer

case class ZooKeeperStorageEnvelope(metadata: Map[String, String], data: ByteBuffer)
