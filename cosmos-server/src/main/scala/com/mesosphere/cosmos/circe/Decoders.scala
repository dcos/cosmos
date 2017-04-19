package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.universe.common.circe.Decoders._
import io.circe.Decoder
import io.circe.Decoder
import io.circe.Error
import io.circe.Json
import io.circe.generic.semiauto._
import java.nio.charset.StandardCharsets
import java.util.Base64

object Decoders {

  def decode[T: Decoder](value: String): T = {
    io.circe.jawn.decode[T](value) match {
      case Right(result) => result
      case Left(error) => throw CirceError(error)
    }
  }

  def parse(value: String): Json = {
    io.circe.jawn.parse(value) match {
      case Right(result) => result
      case Left(error) => throw CirceError(error)
    }
  }

  def decode64[T: Decoder](str: String): T = {
    decode[T](new String(Base64.getDecoder.decode(str), StandardCharsets.UTF_8))
  }

  def parse64(str: String): Json = {
    parse(new String(Base64.getDecoder.decode(str), StandardCharsets.UTF_8))
  }

  implicit val decodeStorageEnvelope: Decoder[StorageEnvelope] =
    deriveDecoder[StorageEnvelope]

}
