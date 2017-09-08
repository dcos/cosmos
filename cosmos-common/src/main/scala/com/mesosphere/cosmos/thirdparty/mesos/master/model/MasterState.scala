package com.mesosphere.cosmos.thirdparty.mesos.master.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class MasterState(frameworks: List[Framework])

object MasterState {
  implicit val encodeMasterState: Encoder[MasterState] = deriveEncoder[MasterState]
  implicit val decodeMasterState: Decoder[MasterState] = deriveDecoder[MasterState]
}
