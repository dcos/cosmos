package com.mesosphere.cosmos.converter

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.twitter.bijection.Bijection
import io.circe.jawn.decode
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64

import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate

object Storage {

  implicit val packageCoordinateToBase64String
  : Bijection[PackageCoordinate, String] = {
    def fwd(coordinate: PackageCoordinate): String = {
      Base64.getEncoder.encodeToString(
        coordinate.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      )
    }

    def rev(str: String): PackageCoordinate = {
      val coordinate  = new String(
        Base64.getDecoder.decode(str),
        StandardCharsets.UTF_8
      )

      decode[PackageCoordinate](coordinate) match {
        case Xor.Left(err) => throw CirceError(err)
        case Xor.Right(c) => c
      }
    }

    Bijection.build[PackageCoordinate, String](fwd)(rev)
  }

}
