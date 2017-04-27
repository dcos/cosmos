package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.CompoundMediaTypeOps._
import com.mesosphere.cosmos.http.{CompoundMediaType, MediaType}
import io.circe.Encoder

/** Allows an [[io.circe.Encoder]] to be selected by a [[com.mesosphere.cosmos.http.MediaType]]. */
final class DispatchingMediaTypedEncoder[A] private(
  private[this] val encoders: Map[MediaType, MediaTypedEncoder[A]]
) {

  def apply(cmt: CompoundMediaType): Option[MediaTypedEncoder[A]] = {
    cmt.getMostAppropriateMediaType(encoders.keySet).flatMap(encoders.get)
  }

  def mediaTypes: Set[MediaType] = encoders.keySet

}

object DispatchingMediaTypedEncoder {

  def apply[A](encoders: Set[MediaTypedEncoder[A]]): DispatchingMediaTypedEncoder[A] = {
    new DispatchingMediaTypedEncoder(
      encoders.flatMap { encoder =>
        encoder.mediaTypes.toList.map { mediaType =>
          mediaType -> encoder
        }
      }.toMap
    )
  }

  def apply[A](mediaType: MediaType)(implicit
    encoder: Encoder[A]
  ): DispatchingMediaTypedEncoder[A] = {
    DispatchingMediaTypedEncoder(Set(MediaTypedEncoder(encoder, mediaType)))
  }

}
