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
  def apply[A : Decoder](mediaTypes: NonEmptyList[MediaType]): MediaTypedDecoder[A] = {
    new MediaTypedDecoder(implicitly[Decoder[A]], mediaTypes)
  }

  def apply[A : Decoder](mediaType: MediaType): MediaTypedDecoder[A] = {
    MediaTypedDecoder(NonEmptyList.of(mediaType))
  }

}
