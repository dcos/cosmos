package com.mesosphere.cosmos.circe

import cats.data.Xor
import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import com.mesosphere.universe.common.circe.Decoders._
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {
  def decode[T](value: String)(implicit decoder: Decoder[T]): T = {
    io.circe.jawn.decode[T](value) match {
      case Xor.Right(result) => result
      case Xor.Left(error) => throw CirceError(error)
    }
  }

  implicit val decodeZooKeeperStorageEnvelope: Decoder[ZooKeeperStorageEnvelope] =
    deriveDecoder[ZooKeeperStorageEnvelope]

}
