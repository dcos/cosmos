package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class UnsupportedContentEncoding(
  supported: List[String],
  actual: Option[String] = None
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    val acceptMsg = supported.mkString("[", ", ", "]")
    actual match {
      case Some(mt) =>
        s"Unsupported Content-Encoding: $mt Accept-Encoding: $acceptMsg"
      case None =>
        s"Unspecified Content-Encoding Accept-Encoding: $acceptMsg"
    }
  }
}

object UnsupportedContentEncoding {
  implicit val encoder: Encoder[UnsupportedContentEncoding] = deriveEncoder
}
