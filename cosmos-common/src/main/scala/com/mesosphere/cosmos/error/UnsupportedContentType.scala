package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.http.MediaType
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class UnsupportedContentType(
  supported: List[MediaType],
  actual: Option[String] = None
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    val acceptMsg = supported.map(_.show).mkString("[", ", ", "]")
    actual match {
      case Some(mt) =>
        s"Unsupported Content-Type: $mt Accept: $acceptMsg"
      case None =>
        s"Unspecified Content-Type Accept: $acceptMsg"
    }
  }
}

object UnsupportedContentType {
  def forMediaType(
    supported: List[MediaType],
    actual: Option[MediaType]
  ): UnsupportedContentType = {
    new UnsupportedContentType(supported, actual.map(_.show))
  }

  implicit val encoder: Encoder[UnsupportedContentType] = deriveEncoder
}
