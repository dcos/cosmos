package com.mesosphere.cosmos.thirdparty.mesos.master.circe

import com.mesosphere.cosmos.thirdparty.mesos.master.model._
import io.circe.Encoder
import io.circe.generic.semiauto._

object Encoders {
  implicit val encodeFramework: Encoder[Framework] = deriveFor[Framework].encoder
  implicit val encodeMasterState: Encoder[MasterState] = deriveFor[MasterState].encoder
  implicit val encodeMesosFrameworkTearDownResponse: Encoder[MesosFrameworkTearDownResponse] = deriveFor[MesosFrameworkTearDownResponse].encoder
}
