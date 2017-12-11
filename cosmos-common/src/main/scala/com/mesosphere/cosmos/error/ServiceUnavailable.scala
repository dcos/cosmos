package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.netty.handler.codec.http.HttpResponseStatus
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class ServiceUnavailable(
  serviceName: String,
  override val status: HttpResponseStatus = HttpResponseStatus.SERVICE_UNAVAILABLE
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unable to complete request due to service [$serviceName] unavailability"
  }

}

object ServiceUnavailable {
  implicit val encoder: Encoder[ServiceUnavailable] = deriveEncoder
}
