package com.mesosphere.universe.v3.model

import cats.syntax.either._
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.DecodingFailure
import io.circe.syntax.EncoderOps

final class ReleaseVersion private(val value: Long) extends AnyVal

object ReleaseVersion {

  def apply(value: Long): Try[ReleaseVersion] = {
    if (value >= 0) {
      Return(new ReleaseVersion(value))
    } else {
      val message = s"Expected integer value >= 0 for release version, but found [$value]"
      Throw(new IllegalArgumentException(message))
    }
  }

  implicit val packageDefinitionReleaseVersionOrdering: Ordering[ReleaseVersion] = {
    Ordering.by(_.value)
  }

  implicit val encodePackageDefinitionReleaseVersion: Encoder[ReleaseVersion] = {
    Encoder.instance(_.value.asJson)
  }

  implicit val decodePackageDefinitionReleaseVersion: Decoder[ReleaseVersion] =
    Decoder.instance[ReleaseVersion] { (c: HCursor) =>
      c.as[Long].map(ReleaseVersion(_)).flatMap {
        case Return(v) => Right(v)
        case Throw(e) => Left(DecodingFailure(e.getMessage, c.history))
      }
    }

}
