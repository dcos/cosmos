package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.util.AbsolutePath
import java.time.Instant

case class ObjectStorageItem(
  path: AbsolutePath,
  content: Array[Byte],
  mediaType: Option[MediaType] = None,
  timeCreated: Option[Instant] = None
)
