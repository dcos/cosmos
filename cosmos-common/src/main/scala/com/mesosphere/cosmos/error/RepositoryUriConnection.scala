package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.rpc
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class RepositoryUriConnection(
  repository: rpc.v1.model.PackageRepository,
  cause: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Could not access data at URI for repository [${repository.name}]: ${repository.uri}"
  }
}

object RepositoryUriConnection {
  implicit val encoder: Encoder[RepositoryUriConnection] = deriveEncoder
}
