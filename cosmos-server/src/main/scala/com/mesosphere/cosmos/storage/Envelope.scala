package com.mesosphere.cosmos.storage

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import com.mesosphere.cosmos.{CirceError, EnvelopeError}
import com.mesosphere.cosmos.circe.{MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http.{MediaType, MediaTypeOps, MediaTypes}
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.{MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http.{MediaType, MediaTypeOps}
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import com.mesosphere.cosmos.{CirceError, EnvelopeError}
import com.mesosphere.universe.common.ByteBuffers
import io.circe.jawn.decode
import io.circe.syntax._

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object Envelope {

  def encodeData[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): Array[Byte] = {
    implicit val encoder = mediaTypedEncoder.encoder
    val mediaType = mediaTypedEncoder.mediaType
    val bytes = ByteBuffer.wrap(data.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    ZooKeeperStorageEnvelope(
      metadata = Map("Content-Type" -> mediaType.show),
      data = bytes
    ).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def decodeData[T](data: Array[Byte])(implicit mediaTypedDecoder: MediaTypedDecoder[T]): T = {
    implicit val decoder = mediaTypedDecoder.decoder
    val mediaType = mediaTypedDecoder.mediaType

    decode[ZooKeeperStorageEnvelope](new String(data, StandardCharsets.UTF_8))
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
