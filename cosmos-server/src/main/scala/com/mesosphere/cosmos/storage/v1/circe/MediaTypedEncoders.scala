package com.mesosphere.cosmos.storage.v1.circe

import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository

object MediaTypedEncoders {
  implicit val repositoryListEncoder: MediaTypedEncoder[List[PackageRepository]] =
    MediaTypedEncoder[List[PackageRepository]](MediaTypes.RepositoryList)
}
