package com.mesosphere.cosmos.http

import java.util

import com.google.common.collect.Multimaps
import com.google.common.net.{MediaType => GMediaType}
import com.twitter.util.Try

import scala.collection.JavaConversions._

case class MediaTypeParseError(msg: String, cause: Throwable) extends RuntimeException(msg, cause)

object MediaTypeParser {

  def parse(s: String): Try[MediaType] = {
    Try {
      GMediaType.parse(s)
    }
      .map { mediaType =>
        val parameters = mediaType.parameters()
        val params = Multimaps
          .asMap(parameters)
          .toMap
          .map { case (key: String, v: util.List[String]) =>
            key.toLowerCase -> v.toList.head
          }

        MediaType(
          `type` = mediaType.`type`().toLowerCase,
          subType = MediaTypeSubType.parse(mediaType.subtype()),
          parameters = params
        )
      }
      .handle {
        case iae: IllegalArgumentException =>
          throw new MediaTypeParseError(s"Unable to parse MediaType for input: '$s'", iae)
        case ise: IllegalStateException =>
          throw new MediaTypeParseError(s"Unable to parse MediaType for input: '$s'", ise)
      }
  }
}
