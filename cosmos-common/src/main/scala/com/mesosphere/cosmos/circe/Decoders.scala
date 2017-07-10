package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.error.JsonDecodingError
import com.mesosphere.cosmos.error.JsonParsingError
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.Json
import io.circe.JsonObject
import io.circe.ParsingFailure
import java.nio.charset.StandardCharsets
import java.util.Base64
import jawn.ParseException

object Decoders {

  def decode[T: Decoder](value: String): T = {
    convertToExceptionOfCirceDecodingError(io.circe.jawn.decode[T](value), value)
  }

  def mediaTypedDecode[T](
    value: String,
    mediaType: MediaType
  )(
    implicit decoder: MediaTypedDecoder[T]
  ): T = {
    convertToExceptionOfCirceDecodingError(decoder(parse(value).hcursor, mediaType), value)
  }

  def parse(value: String): Json = {
    io.circe.jawn.parse(value) match {
      case Right(result) => result
      case Left(error) => throw JsonParsingError(error,
        populateCirceErrorMetaData(error.asInstanceOf[ParsingFailure], value)).exception
    }
  }

  def decode64[T: Decoder](value: String): T = {
    decode[T](base64DecodeString(value))
  }

  def parse64(value: String): Json = {
    parse(base64DecodeString(value))
  }

  def populateCirceErrorMetaData(
    parsingFailure: ParsingFailure,
    jsonData: String
  ): Option[JsonObject] = {
    if (parsingFailure.underlying.isInstanceOf[ParseException]) {
      val parseException = parsingFailure.underlying.asInstanceOf[ParseException]
      val line = parseException.line
      val index = parseException.index
      val col = parseException.col
      val fieldList = List(
        ("line", Json.fromInt(line)),
        ("index", Json.fromInt(index)),
        ("col", Json.fromInt(col)),
        ("string", Json.fromString(jsonData))
      )
      Json.fromFields(fieldList).asObject
    } else None
  }

  def convertToExceptionOfCirceDecodingError[T](
    result: Either[Error, T],
    value: String
  ): T = result match {
    case Right(result) => result
    case Left(error) => {
      val df = error.asInstanceOf[DecodingFailure]
      throw JsonDecodingError(typeName = df.message, value).exception
    }
  }

  private[this] def base64DecodeString(value: String): String = {
    new String(Base64.getDecoder.decode(value), StandardCharsets.UTF_8)
  }

}
