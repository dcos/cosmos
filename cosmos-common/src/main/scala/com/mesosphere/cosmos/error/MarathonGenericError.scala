package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.netty.handler.codec.http.HttpResponseStatus
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder


final case class MarathonGenericError(
  marathonStatus: HttpResponseStatus,
  override val status: HttpResponseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Received response status code ${marathonStatus.code} from Marathon"
  }

}

object MarathonGenericError {
  implicit val encoder: Encoder[MarathonGenericError] = deriveEncoder
}
