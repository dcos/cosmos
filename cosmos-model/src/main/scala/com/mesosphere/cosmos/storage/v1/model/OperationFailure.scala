package com.mesosphere.cosmos.storage.v1.model

import com.mesosphere.cosmos.rpc

case class OperationFailure(operation: Operation, error: rpc.v1.model.ErrorResponse)
