package com.mesosphere.universe.common.circe

import com.netaporter.uri.Uri
import io.circe.{Decoder, DecodingFailure}

import java.nio.ByteBuffer
import java.util.Base64

object Decoders {

  implicit val decodeUri: Decoder[Uri] = Decoder.decodeString.map(Uri.parse)

  implicit val decodeByteBuffer: Decoder[ByteBuffer] = Decoder.instance { c =>
    c.as[String].bimap(
      { e => DecodingFailure("Base64 string value expected", c.history) },
      { s => ByteBuffer.wrap(Base64.getDecoder.decode(s)) }
    )
  }

}
