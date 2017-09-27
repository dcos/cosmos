package com.mesosphere.cosmos.error

import com.twitter.util.StorageUnit
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import com.mesosphere.cosmos.circe.Encoders._

final case class InvalidContentLengthLimit(limit: StorageUnit) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Invalid content length limit set [$limit]"
}

object InvalidContentLengthLimit {
  implicit val encoder: Encoder[InvalidContentLengthLimit] = deriveEncoder
}
