package com.mesosphere.cosmos.model

import com.netaporter.uri.Uri
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

/** Normalizes the representation of Marathon app IDs to ensure we can compare them correctly.
  *
  * Stores the app ID as a string for the common case where we're just passing it along with other
  * data. Converting it to a URI to invoke a Marathon endpoint should happen more rarely.
  *
  * Extends [[scala.AnyVal]] to avoid allocation overhead.
  */
final class AppId private(override val toString: String) extends AnyVal {

  def toUri: Uri = Uri.parse(toString)

}

object AppId {

  def apply(s: String): AppId = new AppId(if (s.startsWith("/")) s else "/" + s)

  implicit val encoder: Encoder[AppId] = Encoder.instance(_.toString.asJson)

  implicit val decoder: Decoder[AppId] = Decoder.decodeString.map(AppId(_))

}
