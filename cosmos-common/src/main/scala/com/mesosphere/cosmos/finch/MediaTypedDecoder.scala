package com.mesosphere.cosmos.finch

import cats.data.NonEmptyList
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeOps
import io.circe.Decoder
import io.circe.HCursor

/** Associates a media type with an [[io.circe.Decoder]] instance. */
trait MediaTypedDecoder[A] {
  val decoder: Decoder[A]

  val mediaTypes: NonEmptyList[MediaType]

  def apply(cursor: HCursor, mediaType: MediaType): Decoder.Result[A] = {
    if (mediaTypes.exists(current => MediaTypeOps.compatible(current, mediaType))) {
      decoder(cursor)
    } else {
      throw new IllegalArgumentException(
        s"Error while trying to decode JSON. " +
        s"Expected media type '${mediaTypes.toList.map(_.show).mkString(", ")}' " +
        s"actual '${mediaType.show}'"
      )
    }
  }
}

final class SimpleMediaTypedDecoder[A](
  val decoder: Decoder[A],
  val mediaTypes: NonEmptyList[MediaType]
) extends MediaTypedDecoder[A]

object MediaTypedDecoder {
  def apply[A : Decoder](mediaTypes: NonEmptyList[MediaType]): MediaTypedDecoder[A] = {
    new SimpleMediaTypedDecoder(implicitly[Decoder[A]], mediaTypes)
  }

  def apply[A : Decoder](mediaType: MediaType): MediaTypedDecoder[A] = {
    MediaTypedDecoder(NonEmptyList.of(mediaType))
  }

}
