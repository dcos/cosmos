package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class ServiceUnavailable(
  serviceName: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unable to complete request due to service [$serviceName] unavailability"
  }

  override def status: Status = Status.ServiceUnavailable
}

object ServiceUnavailable {
  implicit val encoder: Encoder[ServiceUnavailable] = deriveEncoder
}
