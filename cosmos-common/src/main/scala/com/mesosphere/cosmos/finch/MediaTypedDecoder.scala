package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder

/** Associates a media type with an [[io.circe.Decoder]] instance. */
final class MediaTypedDecoder[A] private(val decoder: Decoder[A], val mediaTypes: List[MediaType])

object MediaTypedDecoder {

  def apply[A](mediaTypes: List[MediaType])(implicit decoder: Decoder[A]): MediaTypedDecoder[A] = {
    MediaTypedDecoder(decoder, mediaTypes)
  }

  def apply[A : Decoder](mediaType: MediaType): MediaTypedDecoder[A] = {
    apply[A](List(mediaType))
  }

  def apply[A](decoder: Decoder[A], mediaTypes: List[MediaType]): MediaTypedDecoder[A] = {
    new MediaTypedDecoder(decoder, mediaTypes)
  }

}
