package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.http.MediaType
import io.finch.DecodeRequest

import scala.reflect.ClassTag

/** Associates relevant metadata with an [[io.finch.DecodeRequest]] instance. */
final class MediaTypedDecoder[A] private(
  val decoder: DecodeRequest[A],
  val mediaType: MediaType,
  val classTag: ClassTag[A]
)

object MediaTypedDecoder {

  def apply[A](mediaType: MediaType)(implicit
    decoder: DecodeRequest[A],
    classTag: ClassTag[A]
  ): MediaTypedDecoder[A] = {
    MediaTypedDecoder(decoder, mediaType, classTag)
  }

  def apply[A](
    decoder: DecodeRequest[A],
    mediaType: MediaType,
    classTag: ClassTag[A]
  ): MediaTypedDecoder[A] = {
    new MediaTypedDecoder(decoder, mediaType, classTag)
  }

}
