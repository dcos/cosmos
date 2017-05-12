package com.mesosphere.cosmos.thirdparty.mesos.master.circe

import com.mesosphere.cosmos.thirdparty.mesos.master.model._
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {
  implicit val decodeFramework: Decoder[Framework] = deriveDecoder[Framework]
  implicit val decodeMasterState: Decoder[MasterState] = deriveDecoder[MasterState]
  implicit val decodeMesosFrameworkTearDownResponse: Decoder[MesosFrameworkTearDownResponse] = deriveDecoder[MesosFrameworkTearDownResponse]
}
