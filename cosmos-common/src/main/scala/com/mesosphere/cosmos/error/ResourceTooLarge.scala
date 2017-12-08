package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class ResourceTooLarge(contentLength: Option[Long], limit: Long) extends CosmosError {

  override def message: String = {
    val lengthText = contentLength.map(_ + " bytes").getOrElse("unknown")
    s"Resource of $lengthText length has reached or exceeded the limit of $limit bytes"
  }

  override def data: Option[JsonObject] = CosmosError.deriveData(this)

  override def status: Status = Status.Forbidden

}

object ResourceTooLarge {
  implicit val encoder: Encoder[ResourceTooLarge] = deriveEncoder
}
