package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder

/** Associates a media type with an [[io.circe.Decoder]] instance. */
final class MediaTypedDecoder[A] private(val decoder: Decoder[A], val mediaType: MediaType)

object MediaTypedDecoder {

  def apply[A](mediaType: MediaType)(implicit
    decoder: Decoder[A]
  ): MediaTypedDecoder[A] = {
    MediaTypedDecoder(decoder, mediaType)
  }

  def apply[A](decoder: Decoder[A], mediaType: MediaType): MediaTypedDecoder[A] = {
    new MediaTypedDecoder(decoder, mediaType)
  }

}
