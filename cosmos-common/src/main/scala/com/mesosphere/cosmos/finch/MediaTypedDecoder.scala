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
  val decoder: Decoder[A]

  val classTag: ClassTag[A]

  val mediaTypes: NonEmptyList[MediaType]

  def apply(cursor: HCursor, mediaType: MediaType): Result[A] = {
    if (mediaTypes.exists(current => MediaType.compatible(current, mediaType))) {
      implicit val tag = classTag
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

final class SimpleMediaTypedDecoder[A](
  val decoder: Decoder[A],
  val classTag: ClassTag[A],
  val mediaTypes: NonEmptyList[MediaType]
) extends MediaTypedDecoder[A]

object MediaTypedDecoder {
  def apply[A](
    mediaTypes: NonEmptyList[MediaType]
  )(
    implicit decoder: Decoder[A],
    classTag: ClassTag[A]
  ): MediaTypedDecoder[A] = {
    new SimpleMediaTypedDecoder(decoder, classTag, mediaTypes)
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
