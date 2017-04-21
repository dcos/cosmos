package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import io.circe.Encoder

/** Associates a media type with an [[io.circe.Encoder]] instance. */
trait MediaTypedEncoder[A] {
  val encoder: Encoder[A]
  def mediaType(a: A): MediaType
}

object MediaTypedEncoder {

  def apply[A](mediaType: MediaType)(implicit encoder: Encoder[A]): MediaTypedEncoder[A] = {
    MediaTypedEncoder(encoder, mediaType)
  }

  def apply[A](encoderForA: Encoder[A], mediaTypeForA: MediaType): MediaTypedEncoder[A] = {
    new MediaTypedEncoder[A] {
      val encoder = encoderForA
      def mediaType(a: A) = mediaTypeForA
    }
  }

}
