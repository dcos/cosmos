package com.mesosphere.cosmos.finch

import cats.data.NonEmptyList
import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder

/** Associates a media type with an [[io.circe.Decoder]] instance. */
final class MediaTypedDecoder[A] private(
  val decoder: Decoder[A],
  val mediaTypes: NonEmptyList[MediaType]
)

object MediaTypedDecoder {

  def apply[A](
    mediaTypes: NonEmptyList[MediaType]
  )(
    implicit decoder: Decoder[A]
  ): MediaTypedDecoder[A] = {
    MediaTypedDecoder(decoder, mediaTypes)
  }

  def apply[A : Decoder](mediaType: MediaType): MediaTypedDecoder[A] = {
    apply[A](NonEmptyList.of(mediaType))
  }

  def apply[A](decoder: Decoder[A], mediaTypes: NonEmptyList[MediaType]): MediaTypedDecoder[A] = {
    new MediaTypedDecoder(decoder, mediaTypes)
  }

}
