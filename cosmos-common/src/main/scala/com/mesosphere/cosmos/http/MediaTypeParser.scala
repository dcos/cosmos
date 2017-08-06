package com.mesosphere.cosmos.http

import com.google.common.collect.Multimaps
import com.google.common.net.{MediaType => GMediaType}
import com.twitter.util.Try

import scala.collection.JavaConverters._
import java.util

case class MediaTypeParseError(msg: String, cause: Throwable) extends RuntimeException(msg, cause)

object MediaTypeParser {

  def parseUnsafe(s: String): MediaType = parse(s).get

  def parse(s: String): Try[MediaType] = {
    Try {
      GMediaType.parse(s.trim)
    }
      .map { mediaType =>
        val parameters = mediaType.parameters()
        val params = Multimaps
          .asMap(parameters)
          .asScala
          .toMap
          .map { case (key: String, v: util.List[String]) =>
            key.toLowerCase -> v.asScala.toList.head
          }

        MediaType(
          `type` = mediaType.`type`().toLowerCase,
          subType = MediaTypeSubType.parse(mediaType.subtype()),
          parameters = params
        )
      }
      .handle {
        case iae: IllegalArgumentException =>
          throw MediaTypeParseError(s"Unable to parse MediaType for input: '$s'", iae)
        case ise: IllegalStateException =>
          throw MediaTypeParseError(s"Unable to parse MediaType for input: '$s'", ise)
      }
  }
}
