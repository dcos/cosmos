package com.mesosphere.cosmos.storage.v1.circe

import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.storage.installqueue.Operation
import com.mesosphere.cosmos.storage.installqueue.OperationFailure

object MediaTypedEncoders {
  implicit val repositoryListEncoder: MediaTypedEncoder[List[PackageRepository]] =
    MediaTypedEncoder[List[PackageRepository]](MediaTypes.RepositoryList)

  implicit val operationEncoder: MediaTypedEncoder[Operation] =
    MediaTypedEncoder[Operation](MediaTypes.Operation)

  implicit val operationFailureEncoder: MediaTypedEncoder[OperationFailure] =
    MediaTypedEncoder[OperationFailure](MediaTypes.OperationFailure)
}
