package com.mesosphere.cosmos.storage.v1.circe

import com.mesosphere.cosmos.storage.v1.model.ETag
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {
  implicit val decodeEtag: Decoder[ETag] = deriveDecoder[ETag]
}
