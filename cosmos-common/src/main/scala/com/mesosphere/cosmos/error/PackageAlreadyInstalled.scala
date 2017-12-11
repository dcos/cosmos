package com.mesosphere.cosmos.error

import io.netty.handler.codec.http.HttpResponseStatus
import io.circe.JsonObject

final case class PackageAlreadyInstalled(
  override val status: HttpResponseStatus = HttpResponseStatus.CONFLICT
) extends CosmosError {
  override def data: Option[JsonObject] = None

  override def message: String = "Package is already installed"

}
