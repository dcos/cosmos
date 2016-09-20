package com.mesosphere.cosmos.storage

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import com.mesosphere.cosmos.{CirceError, EnvelopeError}
import com.mesosphere.cosmos.circe.{MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http.{MediaType, MediaTypeOps}
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.common.circe.Encoders._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.jawn.decode
import io.circe.syntax._

object Envelope {

  private[this] case class StorageEnvelope(metadata: Map[String, String], data: ByteBuffer)
  private[this] implicit val decodeStorageEnvelope: Decoder[StorageEnvelope] = deriveDecoder
  private[this] implicit val encodeStorageEnvelope: Encoder[StorageEnvelope] = deriveEncoder

  def encodeData[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): Array[Byte] = {
    implicit val encoder = mediaTypedEncoder.encoder
    val mediaType = mediaTypedEncoder.mediaType
    val bytes = ByteBuffer.wrap(data.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    StorageEnvelope(
      metadata = Map("Content-Type" -> mediaType.show),
      data = bytes
    ).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def decodeData[T](data: Array[Byte])(implicit mediaTypedDecoder: MediaTypedDecoder[T]): T = {
    implicit val decoder = mediaTypedDecoder.decoder
    val mediaType = mediaTypedDecoder.mediaType

    decode[StorageEnvelope](new String(data, StandardCharsets.UTF_8))
      .flatMap { envelope =>
        val contentType = envelope.metadata
          .get("Content-Type")
          .flatMap { s => MediaType.parse(s).toOption }

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
      } valueOr { err => throw CirceError(err) }
  }
}
