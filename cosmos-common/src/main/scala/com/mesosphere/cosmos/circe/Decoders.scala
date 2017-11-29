package com.mesosphere.cosmos.circe

import cats.syntax.either._
import com.mesosphere.cosmos.error.CosmosError
import com.mesosphere.cosmos.error.JsonDecodingError
import com.mesosphere.cosmos.error.JsonParsingError
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.error.Result
import com.mesosphere.http.MediaType
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.Json
import io.circe.ParsingFailure
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.reflect.ClassTag
import scala.reflect.classTag

object Decoders {

  implicit val decodeUri: Decoder[Uri] = Decoder.decodeString.map(Uri.parse)

  implicit val decodeString: Decoder[String] = {
    Decoder.decodeString.withErrorMessage("String value expected")
  }

  implicit val decodeByteBuffer: Decoder[ByteBuffer] = Decoder.instance { c =>
    c.as[String].bimap(
      { e => DecodingFailure("Base64 string value expected", c.history) },
      { s => ByteBuffer.wrap(Base64.getDecoder.decode(s)) }
    )
  }

  implicit final class ResultOps[T](
    val value: Result[T]
  ) extends AnyVal {
    def getOrThrow: T = value.fold( ce => throw ce.exception, identity)
  }

  def decode[T: Decoder: ClassTag](value: String): T = {
    convertToCosmosException(io.circe.jawn.decode[T](value), value)
  }

  def mediaTypedDecode[T: ClassTag](
    value: String,
    mediaType: MediaType
  )(
    implicit decoder: MediaTypedDecoder[T]
  ): T = {
    convertToCosmosException(decoder(parse(value).hcursor, mediaType), value)
  }

  def parse(value: String): Json = {
    convertToCosmosException(io.circe.jawn.parse(value), value)
  }

  def decode64[T: Decoder: ClassTag](value: String): T = {
    decode[T](base64DecodeString(value))
  }

  def parse64(value: String): Json = {
    parse(base64DecodeString(value))
  }

  def convertToCosmosException[T: ClassTag](
    result: Either[Error, T],
    inputValue: String
  ): T = convertToCosmosError(result, inputValue).getOrThrow

  def convertToCosmosError[T: ClassTag](
    result: Either[Error, T],
    inputValue: String
  ): Either[CosmosError, T] = result match {
    case Right(value) => Right(value)
    case Left(ParsingFailure(message, underlying)) =>
      Left(
        JsonParsingError(
          underlying.getClass.getName,
          message,
          inputValue
        )
      )
    case Left(DecodingFailure(message, _)) =>
      Left(
        JsonDecodingError(
          classTag[T].runtimeClass.getName,
          message,
          inputValue
        )
      )
  }

  private[this] def base64DecodeString(value: String): String = {
    new String(Base64.getDecoder.decode(value), StandardCharsets.UTF_8)
  }
}
