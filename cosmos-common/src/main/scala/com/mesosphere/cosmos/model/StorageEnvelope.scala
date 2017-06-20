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
import com.twitter.io.StreamIO
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

final case class StorageEnvelope private (metadata: Map[String, String], data: ByteBuffer) {
  def decodeData[T](implicit mediaTypedDecoder: MediaTypedDecoder[T]): T = {
    val contentType = metadata.get(Fields.ContentType).flatMap { contentTypeValue =>
      MediaType.parse(contentTypeValue).toOption
    } getOrElse {
      throw new IllegalArgumentException(
        "Error while trying to deserialize envelope data. Content-Type not defined."
      )
    }

    val storageData = metadata.get(Fields.ContentEncoding).map { encoding =>
      if (encoding == StorageEnvelope.gzipEncoding) {
        val byteStream = new ByteArrayOutputStream()
        val gzipStream = new GZIPInputStream(new ByteArrayInputStream(ByteBuffers.getBytes(data)))

        StreamIO.copy(gzipStream, byteStream)

        byteStream.toByteArray()
      } else {
        throw new IllegalArgumentException(
          "Error while trying to deserialize envelope data. Unknown Content-Encoding: gzip."
        )
      }
    } getOrElse {
      ByteBuffers.getBytes(data)
    }

    mediaTypedDecode(
      new String(storageData, StandardCharsets.UTF_8),
      contentType
    )
  }
}

object StorageEnvelope {
  private val gzipEncoding = "gzip"

  def apply[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): StorageEnvelope = {
    val (json, mediaType) = mediaTypedEncoder(data)

    val bytes = {
      val byteStream = new ByteArrayOutputStream()
      val gzipStream = new GZIPOutputStream(byteStream)
      gzipStream.write(json.noSpaces.getBytes(StandardCharsets.UTF_8))
      gzipStream.close()
      ByteBuffer.wrap(byteStream.toByteArray())
    }

    StorageEnvelope(
      metadata = Map(
        Fields.ContentType -> mediaType.show,
        Fields.ContentEncoding -> gzipEncoding
      ),
      data = bytes
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
