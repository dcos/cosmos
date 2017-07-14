package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class RepositoryAddIndexOutOfBounds(attempted: Int, max: Int) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    val suggestion = if (max == 0) "can be at most 0" else s"must be between 0 and $max inclusive."
    s"Index ($attempted) is out of range. Index value " + suggestion
  }
}

object RepositoryAddIndexOutOfBounds {
  implicit val encoder: Encoder[RepositoryAddIndexOutOfBounds] = deriveEncoder
}
