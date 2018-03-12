package com.mesosphere.cosmos.finch

import cats.data.NonEmptyList
import com.mesosphere.cosmos.circe.Decoders.convertToCosmosError
import com.mesosphere.cosmos.error.UnsupportedContentType
import com.mesosphere.error.Result
import com.mesosphere.http.MediaType
import io.circe.Decoder
import io.circe.HCursor
import scala.reflect.ClassTag

/** Associates a media type with an [[io.circe.Decoder]] instance. */
trait MediaTypedDecoder[A] {
  val mediaTypes: NonEmptyList[MediaType]
  val decoder: Decoder[A]

  def apply(cursor: HCursor, mediaType: MediaType): Result[A]
}

final class SimpleMediaTypedDecoder[A](
  val mediaTypes: NonEmptyList[MediaType],
  val decoder: Decoder[A]
)(
  implicit classTag: ClassTag[A]
) extends MediaTypedDecoder[A] {

  def apply(cursor: HCursor, mediaType: MediaType): Result[A] = {
    if (mediaTypes.exists(current => MediaType.compatible(current, mediaType))) {
      convertToCosmosError(decoder(cursor), cursor.value.noSpaces)
    } else {
      Left(
        UnsupportedContentType(
          mediaTypes.toList,
          Some(mediaType.show)
        )
      )
    }
  }
}

object MediaTypedDecoder {
  def apply[A](
    mediaTypes: NonEmptyList[MediaType]
  )(
    implicit decoder: Decoder[A],
    classTag: ClassTag[A]
  ): MediaTypedDecoder[A] = {
    new SimpleMediaTypedDecoder(mediaTypes, decoder)
  }

  def apply[A](
    mediaType: MediaType
  )(
    implicit decoder: Decoder[A],
    classTag: ClassTag[A]
  ): MediaTypedDecoder[A] = {
    MediaTypedDecoder(NonEmptyList.of(mediaType))
  }

}
