package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.universe
import com.twitter.bijection.Injection
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Try

final case class PackageCoordinate(name: String, version: universe.v3.model.Version)

object PackageCoordinate {
  implicit val encodePackageCoordinate: Encoder[PackageCoordinate] = deriveEncoder[PackageCoordinate]
  implicit val decodePackageCoordinate: Decoder[PackageCoordinate] = deriveDecoder[PackageCoordinate]

  implicit val stringInjection: Injection[PackageCoordinate, String] = {
    def fwd(coordinate: PackageCoordinate): String = {
      Base64.getUrlEncoder.encodeToString(
        coordinate.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      )
    }

    def rev(str: String): Try[PackageCoordinate] = {
      Try {
        val coordinate  = new String(
          Base64.getUrlDecoder.decode(str),
          StandardCharsets.UTF_8
        )

        decode[PackageCoordinate](coordinate)
      }
    }

    Injection.build[PackageCoordinate, String](fwd)(rev)
  }
}
