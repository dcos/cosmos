package com.mesosphere.cosmos.http

import com.twitter.util.Try


case class MediaTypeSubType(value: String, suffix: Option[String] = None)
object MediaTypeSubType {
  def parse(s: String): MediaTypeSubType = {
    s.split('+').toList match {
      case v :: suffix :: Nil => new MediaTypeSubType(v.toLowerCase, Some(suffix.toLowerCase))
      case v :: Nil => new MediaTypeSubType(v.toLowerCase, None)
      case _ => throw new IllegalStateException(s"Unable to parse suffix from sub-type for value: '$s'")
    }
  }
}

/**
  * https://www.w3.org/Protocols/rfc1341/4_Content-Type.html
  *
  */
case class MediaType(
  `type`: String,
  subType: MediaTypeSubType,
  parameters: Map[String, String] = Map.empty
) {

  def show: String = {
    val t = subType match {
      case MediaTypeSubType(st, Some(suf)) =>
        s"${`type`}/$st+$suf"
      case MediaTypeSubType(st, None) =>
        s"${`type`}/$st"
    }
    val p = parameters.toVector
      .map { case (key, value) => s";$key=$value" }
      .mkString

    t + p
  }
}


object MediaType {

  def unapply(s: String): Option[MediaType] = {
    parse(s).toOption
  }

  def apply(t: String, st: String): MediaType = {
    MediaType(t, MediaTypeSubType(st))
  }

  def parse(s: String): Try[MediaType] = {
    MediaTypeParser.parse(s)
  }

}
