package com.mesosphere.cosmos.circe

import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.cosmos.ErrorResponse
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {

  implicit val decodeErrorResponse: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]

  implicit val decodeZooKeeperStorageEnvelope: Decoder[ZooKeeperStorageEnvelope] =
    deriveDecoder[ZooKeeperStorageEnvelope]

}
