package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.JsonObject

final case class PackageAlreadyInstalled() extends CosmosError {
  override def data: Option[JsonObject] = None

  override def message: String = "Package is already installed"

  override def status: Status = Status.Conflict

  override def exception: CosmosException = {
    CosmosException(this, status, Map.empty, None)
  }
}
