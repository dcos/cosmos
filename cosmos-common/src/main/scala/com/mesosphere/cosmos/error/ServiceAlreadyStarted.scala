package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.JsonObject

final case class ServiceAlreadyStarted() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "The DC/OS service has already been started"

  override def exception: CosmosException = {
    CosmosException(this, Status.Conflict, Map.empty, None)
  }
}
