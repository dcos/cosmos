package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import io.circe.Encoder

/** Associates a media type with an [[io.circe.Encoder]] instance. */
trait MediaTypedEncoder[A] {
  /// Describes the JSON encoder associated with this type.
  val encoder: Encoder[A]

  /// Enumerate all of the possible media types for this type.
  val mediaTypes: List[MediaType]

  /// Returns the specific media type based on the value of the type.
  def mediaType(a: A): MediaType
}

final class SimpleMediaTypedEncoder[A](
  val encoder: Encoder[A],
  mediaTypeForA: MediaType
) extends MediaTypedEncoder[A] {
  val mediaTypes = List(mediaTypeForA)
  def mediaType(a: A): MediaType = mediaTypeForA
}

object MediaTypedEncoder {

  def apply[A](mediaType: MediaType)(implicit encoder: Encoder[A]): MediaTypedEncoder[A] = {
    MediaTypedEncoder(encoder, mediaType)
  }

  def apply[A](encoder: Encoder[A], mediaType: MediaType): MediaTypedEncoder[A] = {
    new SimpleMediaTypedEncoder(encoder, mediaType)
  }

}
