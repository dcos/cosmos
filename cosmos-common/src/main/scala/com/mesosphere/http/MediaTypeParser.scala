package com.mesosphere.http

import com.google.common.collect.Multimaps
import com.google.common.net.{MediaType => GMediaType}
import java.util.{List => JList}
import scala.collection.JavaConverters._
import scala.util.Failure
import scala.util.Try
import scala.util.control.NonFatal

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
          .map { case (key: String, v: JList[String]) =>
            key.toLowerCase -> v.asScala.toList.head
          }

        MediaType(
          `type` = mediaType.`type`().toLowerCase,
          subType = MediaTypeSubType.parse(mediaType.subtype()),
          parameters = params
        )
      }
      .recoverWith {
        case NonFatal(ex) =>
          Failure(MediaTypeParseError(s"Unable to parse MediaType for input: '$s'", ex))
      }
  }
}
