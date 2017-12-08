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
    s"Unable to complete request due to Forbidden response from service " +
      s"[$serviceName]$destinationMessage"
  }

  override def status: Status = Status.Forbidden

  override def exception: CosmosException = {
    CosmosException(this, status, Map.empty, None)
  }

  private def destinationMessage:String = {
    destination match {
      case Some(endpoint) => s" while accessing$endpoint"
      case None => ""
    }
  }
}

object Forbidden {
  implicit val encoder: Encoder[Forbidden] = deriveEncoder
}
