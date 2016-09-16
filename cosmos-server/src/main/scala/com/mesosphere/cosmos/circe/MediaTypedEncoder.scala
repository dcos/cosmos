package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.http.MediaType
import io.circe.Encoder

/** Associates a media type with an [[io.circe.Encoder]] instance. */
final class MediaTypedEncoder[A] private(val encoder: Encoder[A], val mediaType: MediaType)

object MediaTypedEncoder {

  def apply[A](mediaType: MediaType)(implicit
    encoder: Encoder[A]
  ): MediaTypedEncoder[A] = {
    MediaTypedEncoder(encoder, mediaType)
  }

  def apply[A](encoder: Encoder[A], mediaType: MediaType): MediaTypedEncoder[A] = {
    new MediaTypedEncoder(encoder, mediaType)
  }

}
