package com.mesosphere.universe.common.circe

import cats.syntax.either._
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.DecodingFailure
import java.nio.ByteBuffer
import java.util.Base64

object Decoders {

  implicit val decodeString: Decoder[String] = {
    Decoder.decodeString.withErrorMessage("String value expected")
  }

  implicit val decodeUri: Decoder[Uri] = Decoder.decodeString.map(Uri.parse)

  implicit val decodeByteBuffer: Decoder[ByteBuffer] = Decoder.instance { c =>
    c.as[String].bimap(
      { e => DecodingFailure("Base64 string value expected", c.history) },
      { s => ByteBuffer.wrap(Base64.getDecoder.decode(s)) }
    )
  }

  // Work around for Circe issue https://github.com/circe/circe/issues/549
  implicit def decodeListA[A](implicit decoder: Decoder[A]): Decoder[List[A]] = {
    Decoder.decodeCanBuildFrom[A, List]
  }

}
