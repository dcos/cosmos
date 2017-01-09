package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import com.twitter.util.Throw
import com.twitter.util.Try
import com.twitter.io.Buf.ByteArray
import io.finch.Error
import io.finch.items
import java.nio.charset.StandardCharsets

/** Allows a [[com.mesosphere.cosmos.http.MediaType]] to select a request body parsing function. */
final class DispatchingMediaTypedBodyParser[A] private(
  private[this] val parsers: Map[MediaType, Array[Byte] => Try[A]]
) extends (MediaType => Option[Array[Byte] => Try[A]]) {

  def apply(mediaType: MediaType): Option[Array[Byte] => Try[A]] = parsers.get(mediaType)

  def mediaTypes: Set[MediaType] = parsers.keySet

}

object DispatchingMediaTypedBodyParser {

  def apply[A](
    parsers: (MediaType, Array[Byte] => Try[A])*
  ): DispatchingMediaTypedBodyParser[A] = {
    new DispatchingMediaTypedBodyParser(parsers.toMap)
  }

  def parserFromDecoder[A](implicit
    accepts: MediaTypedRequestDecoder[A]
  ): Array[Byte] => Try[A] = { bodyBytes =>
    accepts.decoder(ByteArray.Owned(bodyBytes), StandardCharsets.UTF_8).rescue { case t =>
      Throw(Error.NotParsed(items.BodyItem, accepts.classTag, t))
    }
  }

}
