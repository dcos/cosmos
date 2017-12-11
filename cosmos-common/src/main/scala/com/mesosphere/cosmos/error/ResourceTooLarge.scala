package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.netty.handler.codec.http.HttpResponseStatus
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class ResourceTooLarge(
  contentLength: Option[Long],
  limit: Long,
  override val status: HttpResponseStatus = HttpResponseStatus.FORBIDDEN
) extends CosmosError {

  override def message: String = {
    val lengthText = contentLength.map(_ + " bytes").getOrElse("unknown")
    s"Resource of $lengthText length has reached or exceeded the limit of $limit bytes"
  }

  override def data: Option[JsonObject] = CosmosError.deriveData(this)

}

object ResourceTooLarge {
  implicit val encoder: Encoder[ResourceTooLarge] = deriveEncoder
}
