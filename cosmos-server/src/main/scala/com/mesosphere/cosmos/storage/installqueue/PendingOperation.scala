package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.Operation

case class PendingOperation(
  packageCoordinate: rpc.v1.model.PackageCoordinate,
  operation: Operation,
  failure: Option[OperationFailure]
)
