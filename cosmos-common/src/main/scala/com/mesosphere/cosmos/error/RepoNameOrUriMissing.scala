package com.mesosphere.cosmos.error

import io.circe.JsonObject

final case class RepoNameOrUriMissing() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = s"Must specify either the name or URI of the repository"
}
