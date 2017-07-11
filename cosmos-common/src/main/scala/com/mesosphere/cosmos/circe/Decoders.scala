package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.error.JsonDecodingError
import com.mesosphere.cosmos.error.JsonParsingError
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.Json
import io.circe.ParsingFailure
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.reflect.ClassTag

object Decoders {

  def decode[T: Decoder : ClassTag](value: String): T = {
    convertToExceptionOfACosmosError(io.circe.jawn.decode[T](value), value)
  }

  def mediaTypedDecode[T: ClassTag](
    value: String,
    mediaType: MediaType
  )(
    implicit decoder: MediaTypedDecoder[T]
  ): T = {
    convertToExceptionOfACosmosError(decoder(parse(value).hcursor, mediaType), value)
  }

  def parse(value: String): Json = {
    convertToExceptionOfACosmosError(io.circe.jawn.parse(value), value)
  }

  def decode64[T: Decoder: ClassTag](value: String): T = {
    decode[T](base64DecodeString(value))
  }

  def parse64(value: String): Json = {
    parse(base64DecodeString(value))
  }
  import scala.reflect.ClassTag
  import scala.reflect._
  import scala.reflect.runtime.universe._

  def convertToExceptionOfACosmosError[T: ClassTag](
    result: Either[Error, T],
    inputValue: String
  ): T = result match {
    case Right(value) => value
    case Left(ParsingFailure(message, underlying)) =>
      throw JsonParsingError(underlying.getClass.getName, message, inputValue).exception
    case Left(DecodingFailure(message, _)) =>
      val l = classTag[T].runtimeClass.getName()
      throw JsonDecodingError(l, message, inputValue).exception
  }

  private[this] def base64DecodeString(value: String): String = {
    new String(Base64.getDecoder.decode(value), StandardCharsets.UTF_8)
  }

  def updateObject[T: ClassTag](result: Either[Error, T]): String = {
    classTag[T].runtimeClass.getCanonicalName
  }

  def newInstance[T: TypeTag](result: Either[Error, T]): String = {
    val clazz = typeTag[T].mirror.runtimeClass(typeOf[T])
    clazz.newInstance.asInstanceOf[T].toString
  }

}
