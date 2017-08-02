package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.universe
import com.twitter.bijection.Injection
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Common {
  implicit val packageCoordinateToBase64String: Injection[PackageCoordinate, String] = {
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

  implicit val versionToString = Injection.build[universe.v3.model.SemVer, String] { version =>
    version.toString
  } { string =>
    universe.v3.model.SemVer(string).map(Success(_)).getOrElse(
      Failure(new IllegalArgumentException(s"Unable to parse $string as semver"))
    )
  }
}
