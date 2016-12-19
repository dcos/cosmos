package com.mesosphere.cosmos.storage.v1.model

import com.mesosphere.cosmos.rpc
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._

case class PendingOperation(
  operation: Operation,
  failure: Option[OperationFailure]
) {
  def packageCoordinate: rpc.v1.model.PackageCoordinate = operation.v3Package.packageCoordinate
}
