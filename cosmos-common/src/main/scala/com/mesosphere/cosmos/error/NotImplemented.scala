package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import com.twitter.finagle.http.Status

final case class NotImplemented(operationName: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Cosmos has not been configured to support this operation: $operationName"
  }

  override def exception: CosmosException = {
    exception(Status.NotImplemented, Map.empty, None)
  }
}

object NotImplemented {
  implicit val encoder: Encoder[NotImplemented] = deriveEncoder
}
