package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeOps
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

case class StorageEnvelope private (metadata: Map[String, String], data: ByteBuffer) {
  def decodeData[T](implicit mediaTypedDecoder: MediaTypedDecoder[T]): T = {
    implicit val decoder = mediaTypedDecoder.decoder
    val mediaTypes = mediaTypedDecoder.mediaTypes

    val contentType = metadata.get(Fields.ContentType).flatMap { contentTypeValue =>
      MediaType.parse(contentTypeValue).toOption
    }

    contentType match {
      case Some(mt) if mediaTypes.exists(mediaType => MediaTypeOps.compatible(mediaType, mt)) =>
       val dataString: String = new String(
           ByteBuffers.getBytes(data),
           StandardCharsets.UTF_8)
       decode[T](dataString)
      case Some(mt) =>
        throw new IllegalArgumentException(
          s"Error while trying to deserialize envelope data. " +
          s"Expected Content-Type '${mediaTypes.map(_.show).mkString(", ")}' actual '${mt.show}'"
        )
      case None =>
        throw new IllegalArgumentException(
          s"Error while trying to deserialize envelope data. " +
          s"Content-Type not defined."
        )
    }
  }
}

object StorageEnvelope {

  def apply[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): StorageEnvelope = {
    implicit val encoder = mediaTypedEncoder.encoder

    StorageEnvelope(
      metadata = Map(Fields.ContentType -> mediaTypedEncoder.mediaType(data).show),
      data = ByteBuffer.wrap(data.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    )
  }

  def encodeData[T : MediaTypedEncoder](data: T): Array[Byte] = {
    StorageEnvelope(data).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def decodeData[T](data: Array[Byte])(implicit mediaTypedDecoder: MediaTypedDecoder[T]): T = {
    decode[StorageEnvelope](new String(data, StandardCharsets.UTF_8)).decodeData
  }

  implicit val encoder: Encoder[StorageEnvelope] = deriveEncoder[StorageEnvelope]
  implicit val decoder: Decoder[StorageEnvelope] = deriveDecoder[StorageEnvelope]

}
