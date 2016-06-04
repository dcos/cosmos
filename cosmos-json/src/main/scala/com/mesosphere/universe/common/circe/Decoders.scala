package com.mesosphere.universe.common.circe

import com.netaporter.uri.Uri
import io.circe.Decoder

import java.nio.ByteBuffer
import java.util.Base64

object Decoders {

  implicit val decodeUri: Decoder[Uri] = Decoder.decodeString.map(Uri.parse)

  implicit val decodeByteBuffer: Decoder[ByteBuffer] = Decoder.decodeString.map { b64String =>
    ByteBuffer.wrap(Base64.getDecoder.decode(b64String))
  }

}
