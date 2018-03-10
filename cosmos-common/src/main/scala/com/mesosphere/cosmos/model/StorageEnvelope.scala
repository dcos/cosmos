package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.circe.Decoders.decodeByteBuffer
import com.mesosphere.cosmos.circe.Decoders.mediaTypedDecode
import com.mesosphere.cosmos.circe.Encoders.encodeByteBuffer
import com.mesosphere.cosmos.error.UnsupportedContentEncoding
import com.mesosphere.cosmos.error.UnsupportedContentType
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.error.Result
import com.mesosphere.http.MediaType
import com.mesosphere.universe.common.ByteBuffers
import com.twitter.finagle.http.Fields
import com.twitter.io.StreamIO
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import scala.reflect.ClassTag


final case class StorageEnvelope private (metadata: Map[String, String], data: ByteBuffer) {
  def decodeData[T](
    implicit decoder: MediaTypedDecoder[T],
    classTag: ClassTag[T]
  ): Result[T] = {
    for {
      contentType <- envelopeContentType
      storageData <- envelopeStorageData
      result <- mediaTypedDecode(
        new String(storageData, StandardCharsets.UTF_8),
        contentType
      )
    } yield result
  }


  private def envelopeStorageData: Result[Array[Byte]] = {
     metadata.get(Fields.ContentEncoding) match {
      case Some(encoding) =>
        if (encoding == StorageEnvelope.GzipEncoding) {
          Right(StorageEnvelope.decodeGzip(ByteBuffers.getBytes(data)))
        } else {
          Left(
            UnsupportedContentEncoding(
              List(StorageEnvelope.GzipEncoding),
              Some(encoding)
            )
          )
        }
      case None =>
        Right(ByteBuffers.getBytes(data))
    }
  }

  private def envelopeContentType[T](
    implicit decoder: MediaTypedDecoder[T]
  ): Result[MediaType] = {
    metadata.get(Fields.ContentType).flatMap { contentTypeValue =>
      MediaType.parse(contentTypeValue).toOption.map(Right(_))
    } getOrElse {
      Left(
        UnsupportedContentType(
          decoder.mediaTypes.toList,
          None
        )
      )
    }
  }
}

object StorageEnvelope {
  private def encodeGzip(bytes: Array[Byte]): Array[Byte] = {
    val byteStream = new ByteArrayOutputStream()
    val gzipStream = new GZIPOutputStream(byteStream)
    gzipStream.write(bytes)
    gzipStream.close()
    byteStream.toByteArray()
  }

  val GzipEncoding: String = "gzip"

  private def decodeGzip(bytes: Array[Byte]): Array[Byte] = {
    val byteStream = new ByteArrayOutputStream()
    val gzipStream = new GZIPInputStream(new ByteArrayInputStream(bytes))

    StreamIO.copy(gzipStream, byteStream)

    byteStream.toByteArray()
  }

  def apply[T](data: T)(implicit mediaTypedEncoder: MediaTypedEncoder[T]): StorageEnvelope = {
    val (json, mediaType) = mediaTypedEncoder(data)

    StorageEnvelope(
      metadata = Map(
        Fields.ContentType -> mediaType.show,
        Fields.ContentEncoding -> GzipEncoding
      ),
      data = ByteBuffer.wrap(encodeGzip(json.noSpaces.getBytes(StandardCharsets.UTF_8)))
    )
  }

  def encodeData[T: MediaTypedEncoder](data: T): Array[Byte] = {
    StorageEnvelope(data).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def decodeData[T: MediaTypedDecoder: ClassTag](data: Array[Byte]): Result[T] = {
    decode[StorageEnvelope](
      new String(data, StandardCharsets.UTF_8)
    ).flatMap(_.decodeData)
  }

  implicit val encoder: Encoder[StorageEnvelope] = deriveEncoder[StorageEnvelope]
  implicit val decoder: Decoder[StorageEnvelope] = deriveDecoder[StorageEnvelope]

}
