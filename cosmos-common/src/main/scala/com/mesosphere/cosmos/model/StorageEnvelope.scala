package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeOps
import com.mesosphere.cosmos.circe.Decoders.mediaTypedDecode
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.common.circe.Decoders.decodeByteBuffer
import com.mesosphere.universe.common.circe.Encoders.encodeByteBuffer
import com.twitter.finagle.http.Fields
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

final case class StorageEnvelope private (metadata: Map[String, String], data: ByteBuffer) {
  def decodeData[T](implicit mediaTypedDecoder: MediaTypedDecoder[T]): T = {
    val contentType = metadata.get(Fields.ContentType).flatMap { contentTypeValue =>
      MediaType.parse(contentTypeValue).toOption
    } getOrElse {
      throw new IllegalArgumentException(
        "Error while trying to deserialize envelope data. Content-Type not defined."
      )
    }

    mediaTypedDecode(
      new String(ByteBuffers.getBytes(data), StandardCharsets.UTF_8),
      contentType
    )
  }
}

object StorageEnvelope {

  def apply[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): StorageEnvelope = {
    val (json, mediaType) = mediaTypedEncoder(data)

    StorageEnvelope(
      metadata = Map(Fields.ContentType -> mediaType.show),
      data = ByteBuffer.wrap(json.noSpaces.getBytes(StandardCharsets.UTF_8))
    )
  }

  def encodeData[T : MediaTypedEncoder](data: T): Array[Byte] = {
    StorageEnvelope(data).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def decodeData[T : MediaTypedDecoder](data: Array[Byte]): T = {
    decode[StorageEnvelope](new String(data, StandardCharsets.UTF_8)).decodeData
  }

  implicit val encoder: Encoder[StorageEnvelope] = deriveEncoder[StorageEnvelope]
  implicit val decoder: Decoder[StorageEnvelope] = deriveDecoder[StorageEnvelope]

}
