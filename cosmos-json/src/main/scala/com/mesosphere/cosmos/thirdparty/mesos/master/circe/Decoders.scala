package com.mesosphere.cosmos.thirdparty.mesos.master.circe

import com.mesosphere.cosmos.thirdparty.mesos.master.model._
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {
  implicit val decodeFramework: Decoder[Framework] = deriveFor[Framework].decoder
  implicit val decodeMasterState: Decoder[MasterState] = deriveFor[MasterState].decoder
  implicit val decodeMesosFrameworkTearDownResponse: Decoder[MesosFrameworkTearDownResponse] = deriveFor[MesosFrameworkTearDownResponse].decoder
}
