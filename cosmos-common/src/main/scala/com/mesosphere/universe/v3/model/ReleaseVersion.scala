package com.mesosphere.universe.v3.model

import cats.syntax.either._
import com.twitter.util.{Return, Throw, Try}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

final class ReleaseVersion private(val value: Long) extends AnyVal

object ReleaseVersion {

  def apply(value: Long): ReleaseVersion = validate(value).get

  def validate(value: Long): Try[ReleaseVersion] = {
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
      c.as[Long].map(validate(_)).flatMap {
        case Return(v) => Right(v)
        case Throw(e) => Left(DecodingFailure(e.getMessage, c.history))
      }
    }

}
