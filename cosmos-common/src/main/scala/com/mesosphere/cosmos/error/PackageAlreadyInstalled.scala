package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.JsonObject

final case object PackageAlreadyInstalled extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "Package is already installed"

  override def exception: CosmosException = {
    CosmosException(this, Status.Conflict, Map.empty, None)
  }
}
