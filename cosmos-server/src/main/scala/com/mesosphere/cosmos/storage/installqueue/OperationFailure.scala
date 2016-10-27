package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.rpc

case class OperationFailure(operation: Operation, error: rpc.v1.model.ErrorResponse)
