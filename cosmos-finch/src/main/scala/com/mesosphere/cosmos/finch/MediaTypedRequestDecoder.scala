package com.mesosphere.cosmos.finch

import io.finch.DecodeRequest
import io.finch.circe.decodeCirce

import scala.reflect.ClassTag

/** Associates relevant metadata with an [[io.finch.DecodeRequest]] instance. */
final class MediaTypedRequestDecoder[A] private(
  val mediaTypedDecoder: MediaTypedDecoder[A],
  val classTag: ClassTag[A]
) {
  val decoder: DecodeRequest[A] = decodeCirce(mediaTypedDecoder.decoder)
}

object MediaTypedRequestDecoder {

  def apply[A](mediaTypedDecoder: MediaTypedDecoder[A])(implicit
    classTag: ClassTag[A]
  ): MediaTypedRequestDecoder[A] = {
    new MediaTypedRequestDecoder(mediaTypedDecoder, classTag)
  }

}
