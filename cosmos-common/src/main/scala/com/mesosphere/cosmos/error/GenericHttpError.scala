package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import org.jboss.netty.handler.codec.http.HttpMethod

final case class GenericHttpError(
  method: HttpMethod = HttpMethod.GET,
  uri: Uri,
  clientStatus: Status
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unexpected upstream http error: ${method.getName} ${uri.toString} ${clientStatus.code}"
  }

  def exception(status: Status): CosmosException = {
    CosmosException(this, status, Map.empty, None)
  }
}

object GenericHttpError {
  implicit val encoder: Encoder[GenericHttpError] = deriveEncoder
}
