package com.mesosphere.cosmos.storage.v1.circe

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository

object MediaTypedDecoders {
  implicit val repositoryListDecoder: MediaTypedDecoder[List[PackageRepository]] =
    MediaTypedDecoder[List[PackageRepository]](MediaTypes.RepositoryList)
}
