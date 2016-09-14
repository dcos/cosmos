package com.mesosphere.cosmos.converter

import cats.data.Xor

import com.mesosphere.cosmos.storage.PackageCoordinate
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.universe.common.ByteBuffers
import com.twitter.bijection.Bijection
import com.twitter.util._

import io.circe.Encoder
import io.circe.Decoder
import io.circe.jawn.decode
import io.circe.syntax._

import java.nio.charset.StandardCharsets
import java.util.Base64

object Storage {

  implicit val packageCoordinateToBase64String
  : Bijection[PackageCoordinate, String] = {
    def fwd(coord: PackageCoordinate): String = {
      Base64.getEncoder.encodeToString(
        coord.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      )
    }

    def rev(str: String): PackageCoordinate = {
      val coord  = new String(
        Base64.getDecoder.decode(str),
        StandardCharsets.UTF_8
      )

      decode[PackageCoordinate](coord) match {
        case Xor.Left(err) => throw new CirceError(err)
        case Xor.Right(c) => c
      }
    }

    Bijection.build[PackageCoordinate, String](fwd)(rev)
  }

}
