package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.EnvelopeError
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeOps
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.universe.common.ByteBuffers
import com.twitter.finagle.http.Fields
import io.circe.syntax._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object Envelope {

  def encodeData[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): Array[Byte] = {
    implicit val encoder = mediaTypedEncoder.encoder
    val mediaType = mediaTypedEncoder.mediaType
    val bytes = ByteBuffer.wrap(data.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    StorageEnvelope(
      metadata = Map(Fields.ContentType -> mediaType.show),
      data = bytes
    ).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def decodeData[T](data: Array[Byte])(implicit mediaTypedDecoder: MediaTypedDecoder[T]): T = {
    implicit val decoder = mediaTypedDecoder.decoder
    val mediaType = mediaTypedDecoder.mediaType

    val envelope = decode[StorageEnvelope](new String(data, StandardCharsets.UTF_8))

    val contentType = envelope.metadata
      .get(Fields.ContentType)
      .flatMap(s => MediaType.parse(s).toOption)

    contentType match {
      case Some(mt) if MediaTypeOps.compatible(mediaType, mt) =>
       val dataString: String = new String(
           ByteBuffers.getBytes(envelope.data),
           StandardCharsets.UTF_8)
       decode[T](dataString)
      case Some(mt) =>
        throw EnvelopeError(
          s"Error while trying to deserialize data. " +
          s"Expected Content-Type '${mediaType.show}' actual '${mt.show}'"
        )
      case None =>
        throw EnvelopeError(
          s"Error while trying to deserialize data. " +
          s"Content-Type not defined."
        )
    }
  }
}
