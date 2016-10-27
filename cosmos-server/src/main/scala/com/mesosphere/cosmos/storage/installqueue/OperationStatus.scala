package com.mesosphere.cosmos.storage.installqueue

case class OperationStatus(operation: Option[Operation], failure: Option[OperationFailure])
