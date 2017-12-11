package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import io.netty.handler.codec.http.HttpResponseStatus

final case class MarathonBadGateway(
  marathonStatus: HttpResponseStatus,
  override val status: HttpResponseStatus = HttpResponseStatus.BAD_GATEWAY
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Received response status code ${marathonStatus.code} from Marathon"
  }
}

object MarathonBadGateway {
  implicit val encoder: Encoder[MarathonBadGateway] = deriveEncoder
}
