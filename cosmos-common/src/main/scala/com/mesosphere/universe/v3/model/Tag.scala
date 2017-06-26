package com.mesosphere.universe.v3.model

import java.util.regex.Pattern

import cats.syntax.either._
import com.mesosphere.universe.common.circe.Decoders._
import com.twitter.util.{Return, Throw, Try}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

final class Tag private(val value: String) extends AnyVal {

  override def toString: String = value

}

object Tag {

  val packageDetailsTagRegex: String = "^[^\\s]+$"
  val packageDetailsTagPattern: Pattern = Pattern.compile(packageDetailsTagRegex)

  def apply(s: String): Tag = validate(s).get

  def validate(s: String): Try[Tag] = {
    if (packageDetailsTagPattern.matcher(s).matches()) {
      Return(new Tag(s))
    } else {
      Throw(new IllegalArgumentException(
        s"Value '$s' does not conform to expected format $packageDetailsTagRegex"
      ))
    }
  }

  implicit val encodePackageDefinitionTag: Encoder[Tag] = {
    Encoder.instance(_.value.asJson)
  }

  implicit val decodePackageDefinitionTag: Decoder[Tag] =
    Decoder.instance[Tag] { (c: HCursor) =>
      c.as[String].map(validate(_)).flatMap {
        case Return(r) => Right(r)
        case Throw(ex) =>
          val msg = ex.getMessage.replaceAllLiterally("assertion failed: ", "")
          Left(DecodingFailure(msg, c.history))
      }
    }

}
