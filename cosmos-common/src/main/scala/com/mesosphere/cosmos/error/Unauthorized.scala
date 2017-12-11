package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import io.netty.handler.codec.http.HttpResponseStatus

final case class Unauthorized(
  serviceName: String,
  realm: Option[String],
  override val status: HttpResponseStatus = HttpResponseStatus.UNAUTHORIZED
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = {
    s"Unable to complete request due to Unauthorized response from service [$serviceName]"
  }

  override def exception: CosmosException = {
    CosmosException(
      this,
      realm.map(r => Map("WWW-Authenticate" -> r)).getOrElse(Map.empty),
      None
    )
  }
}

object Unauthorized {
  implicit val encoder: Encoder[Unauthorized] = deriveEncoder
}
