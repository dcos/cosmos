package com.mesosphere.cosmos.storage.v1.circe

import com.mesosphere.cosmos.storage.v1.model.ETag
import io.circe.Encoder
import io.circe.generic.semiauto._

object Encoders {
  implicit val encodeEtag: Encoder[ETag] = deriveEncoder[ETag]
}
