package com.mesosphere.cosmos.storage.installqueue

sealed trait OperationStatus
case class Pending(operation: Operation, failure: Option[OperationFailure]) extends OperationStatus
case class Failed(failure: OperationFailure) extends OperationStatus
