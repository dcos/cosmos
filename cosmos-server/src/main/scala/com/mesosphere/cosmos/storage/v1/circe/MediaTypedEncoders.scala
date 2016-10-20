package com.mesosphere.cosmos.storage.v1.circe

import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.{ErrorResponse, PackageRepository}
import com.mesosphere.cosmos.storage.PackageQueueContents

object MediaTypedEncoders {

  implicit val repositoryListEncoder: MediaTypedEncoder[List[PackageRepository]] =
    MediaTypedEncoder[List[PackageRepository]](MediaTypes.RepositoryList)

  implicit val packageQueueContentsEncoder: MediaTypedEncoder[PackageQueueContents] =
    MediaTypedEncoder[PackageQueueContents](MediaTypes.PackageQueueContents)

  implicit val errorResponseEncoder: MediaTypedEncoder[ErrorResponse] =
    MediaTypedEncoder[ErrorResponse](rpc.MediaTypes.ErrorResponse)
}
