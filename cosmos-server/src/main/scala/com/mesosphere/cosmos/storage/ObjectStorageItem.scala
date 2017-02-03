package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import java.time.Instant

case class ObjectStorageItem(
  name: String,
  content: Array[Byte],
  mediaType: Option[MediaType] = None,
  timeCreated: Option[Instant] = None
)
