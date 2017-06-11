package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.error.CirceError
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder
import io.circe.Error
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.util.Base64

object Decoders {

  def decode[T: Decoder](value: String): T = {
    convertToException(io.circe.jawn.decode[T](value))
  }

  def mediaTypedDecode[T](
    value: String,
    mediaType: MediaType
  )(
    implicit decoder: MediaTypedDecoder[T]
  ): T = {
    convertToException(decoder(parse(value).hcursor, mediaType))
  }

  def parse(value: String): Json = {
    convertToException(io.circe.jawn.parse(value))
  }

  def decode64[T: Decoder](value: String): T = {
    decode[T](base64DecodeString(value))
  }

  def parse64(value: String): Json = {
    parse(base64DecodeString(value))
  }

  private[this] def convertToException[T](result: Either[Error, T]): T = result match {
    case Right(result) => result
    case Left(error) => throw CirceError(error).exception
  }

  private[this] def base64DecodeString(value: String): String = {
    new String(Base64.getDecoder.decode(value), StandardCharsets.UTF_8)
  }

}
