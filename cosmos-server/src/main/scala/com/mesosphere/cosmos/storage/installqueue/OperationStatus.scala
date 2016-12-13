package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.rpc.v1.model.Operation

sealed trait OperationStatus
case class Pending(operation: Operation, failure: Option[OperationFailure]) extends OperationStatus
case class Failed(failure: OperationFailure) extends OperationStatus
