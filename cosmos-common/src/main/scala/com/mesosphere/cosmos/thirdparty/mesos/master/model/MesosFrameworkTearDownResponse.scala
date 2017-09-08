package com.mesosphere.cosmos.thirdparty.mesos.master.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class MesosFrameworkTearDownResponse()

object MesosFrameworkTearDownResponse {
  implicit val encodeMesosFrameworkTearDownResponse: Encoder[MesosFrameworkTearDownResponse] =
    deriveEncoder[MesosFrameworkTearDownResponse]
  implicit val decodeMesosFrameworkTearDownResponse: Decoder[MesosFrameworkTearDownResponse] =
    deriveDecoder[MesosFrameworkTearDownResponse]
}
