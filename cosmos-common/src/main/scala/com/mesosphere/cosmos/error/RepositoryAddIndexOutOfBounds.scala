package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class RepositoryAddIndexOutOfBounds(attempted: Int, max: Int) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Index out of range: $attempted"
}

object RepositoryAddIndexOutOfBounds {
  implicit val encoder: Encoder[RepositoryAddIndexOutOfBounds] = deriveEncoder
}
