package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class Forbidden(
  serviceName: String,
  destination : Option[String] = None
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unable to complete request due to Forbidden response from service [$serviceName]" +
      s"${if (destination.isDefined) " while accessing" + destination}"
  }

  override def exception: CosmosException = {
    CosmosException(this, Status.Forbidden, Map.empty, None)
  }
}

object Forbidden {
  implicit val encoder: Encoder[Forbidden] = deriveEncoder
}
