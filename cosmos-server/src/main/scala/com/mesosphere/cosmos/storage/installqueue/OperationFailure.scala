package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.Operation

case class OperationFailure(operation: Operation, error: rpc.v1.model.ErrorResponse)
