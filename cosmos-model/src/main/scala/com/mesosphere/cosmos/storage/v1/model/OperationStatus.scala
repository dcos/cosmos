package com.mesosphere.cosmos.storage.v1.model

sealed trait OperationStatus

case class PendingStatus(operation: Operation, failure: Option[OperationFailure])
  extends OperationStatus

case class FailedStatus(failure: OperationFailure) extends OperationStatus
