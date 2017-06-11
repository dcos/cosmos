package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class Unauthorized(
  serviceName: String,
  realm: Option[String]
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = {
    s"Unable to complete request due to Unauthorized response from service [$serviceName]"
  }

  override def exception: CosmosException = {
    exception(
      Status.Unauthorized,
      realm.map(r => Map("WWW-Authenticate" -> r)).getOrElse(Map.empty),
      None
    )
  }
}

object Unauthorized {
  implicit val encoder: Encoder[Unauthorized] = deriveEncoder
}
