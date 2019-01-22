package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import io.lemonlabs.uri.Uri
import io.netty.handler.codec.http.HttpResponseStatus
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import org.jboss.netty.handler.codec.http.HttpMethod

final case class GenericHttpError(
  method: HttpMethod = HttpMethod.GET,
  uri: Uri,
  clientStatus: HttpResponseStatus,
  override val status: HttpResponseStatus = HttpResponseStatus.BAD_REQUEST
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unexpected upstream http error: ${method.getName} ${uri.toString} ${clientStatus.code}"
  }

}

object GenericHttpError {
  implicit val encoder: Encoder[GenericHttpError] = deriveEncoder
}
