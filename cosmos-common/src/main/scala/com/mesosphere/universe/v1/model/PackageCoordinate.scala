package com.mesosphere.universe.v1.model

import fastparse.all._
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.error.ResultOps
import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import org.apache.commons.codec.binary.Base32
import scala.util.Try

final case class PackageCoordinate(name: String, version: universe.v3.model.Version) {

  override def toString(): String = {
    PackageCoordinate.toString(this)
  }
}

object PackageCoordinate {
  private[this] val base32 = new Base32()

  implicit val encodePackageCoordinate: Encoder[PackageCoordinate] = deriveEncoder[PackageCoordinate]
  implicit val decodePackageCoordinate: Decoder[PackageCoordinate] = deriveDecoder[PackageCoordinate]

  def parse(string: String): Option[PackageCoordinate] = {
    parser.parse(string).fold(
      (_, _, _) => None,
      (pc, _) => Some(pc)
    )
  }

  val parser: Parser[PackageCoordinate] = {
    CharIn('a' to 'z', '0' to '9').rep(1).!.flatMap { string =>
      Try {
        val coordinate  = new String(
          base32.decode(string.toUpperCase),
          StandardCharsets.UTF_8
        )

        decode[PackageCoordinate](coordinate).getOrThrow
      } fold (
        _ => Fail,
        pc => PassWith(pc)
      )
    }
  }

  def toString(pc: PackageCoordinate): String = {
    base32.encodeAsString(
      pc.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    ).toLowerCase.replace("=", "")
  }
}
