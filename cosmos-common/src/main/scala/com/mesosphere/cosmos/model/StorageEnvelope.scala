package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.universe.common.circe.Decoders.decodeByteBuffer
import com.mesosphere.universe.common.circe.Encoders.encodeByteBuffer
import com.twitter.finagle.http.Fields
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

case class StorageEnvelope private (metadata: Map[String, String], data: ByteBuffer)

object StorageEnvelope {

  def apply[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): StorageEnvelope = {
    implicit val encoder = mediaTypedEncoder.encoder

    StorageEnvelope(
      metadata = Map(Fields.ContentType -> mediaTypedEncoder.mediaType(data).show),
      data = ByteBuffer.wrap(data.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    )
  }

  implicit val encoder: Encoder[StorageEnvelope] = deriveEncoder[StorageEnvelope]
  implicit val decoder: Decoder[StorageEnvelope] = deriveDecoder[StorageEnvelope]

}
