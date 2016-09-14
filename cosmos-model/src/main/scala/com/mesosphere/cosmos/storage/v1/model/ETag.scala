package com.mesosphere.cosmos.storage.v1.model

final case class ETag(value: String, isWeak: Option[Boolean] = None)
