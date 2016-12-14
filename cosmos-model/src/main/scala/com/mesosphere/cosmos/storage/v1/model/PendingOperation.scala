package com.mesosphere.cosmos.storage.v1.model

import com.mesosphere.cosmos.rpc

case class PendingOperation(
  packageCoordinate: rpc.v1.model.PackageCoordinate,
  operation: Operation,
  failure: Option[OperationFailure]
)
