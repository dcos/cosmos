package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class UnsupportedRedirect(
  supported: List[String],
  actual: Option[String] = None
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    val supportedMsg = supported.mkString("[", ", ", "]")
    actual match {
      case Some(act) =>
        s"Unsupported redirect scheme - supported: $supportedMsg actual: $act"
      case None =>
        s"Unsupported redirect scheme - supported: $supportedMsg"
    }
  }
}

object UnsupportedRedirect {
  implicit val encoder: Encoder[UnsupportedRedirect] = deriveEncoder
}
