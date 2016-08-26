package com.mesosphere.cosmos.thirdparty.mesos.master.circe

import com.mesosphere.cosmos.thirdparty.mesos.master.model._
import io.circe.Encoder
import io.circe.generic.semiauto._

object Encoders {
  implicit val encodeFramework: Encoder[Framework] = deriveEncoder[Framework]
  implicit val encodeMasterState: Encoder[MasterState] = deriveEncoder[MasterState]
  implicit val encodeMesosFrameworkTearDownResponse: Encoder[MesosFrameworkTearDownResponse] = deriveEncoder[MesosFrameworkTearDownResponse]
}
