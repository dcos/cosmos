package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class OperationInProgress(coordinate: rpc.v1.model.PackageCoordinate) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"A change to package ${coordinate.name}-${coordinate.version} is already in progress"
  }

  override def exception: CosmosException = {
    exception(Status.Conflict, Map.empty, None)
  }
}

object OperationInProgress {
  implicit val encoder: Encoder[OperationInProgress] = deriveEncoder
}
