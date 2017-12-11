package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import io.netty.handler.codec.http.HttpResponseStatus

final case class Forbidden(
  serviceName: String,
  destination : Option[String] = None,
  override val status: HttpResponseStatus = HttpResponseStatus.FORBIDDEN
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unable to complete request due to Forbidden response from service " +
      s"[$serviceName]$destinationMessage"
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
