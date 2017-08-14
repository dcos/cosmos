package com.mesosphere.cosmos.thirdparty.marathon.model

import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax._

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

  implicit val ordering: Ordering[AppId] = Ordering.by(_.toString)

  implicit val decoder: Decoder[AppId] = Decoder.decodeString.map(apply)
  implicit val encoder: Encoder[AppId] = Encoder.instance(_.toString.asJson)

}
