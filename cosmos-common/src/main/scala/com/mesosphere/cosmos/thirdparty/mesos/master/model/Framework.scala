package com.mesosphere.cosmos.thirdparty.mesos.master.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class Framework(
  id: String,
  name: String
)

object Framework {
  implicit val encodeFramework: Encoder[Framework] = deriveEncoder[Framework]
  implicit val decodeFramework: Decoder[Framework] = deriveDecoder[Framework]
}
