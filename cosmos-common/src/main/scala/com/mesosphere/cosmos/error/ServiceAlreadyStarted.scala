package com.mesosphere.cosmos.error

import io.netty.handler.codec.http.HttpResponseStatus
import io.circe.JsonObject

final case class ServiceAlreadyStarted(
  override val status: HttpResponseStatus = HttpResponseStatus.CONFLICT
) extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "The DC/OS service has already been started"

}
