package com.mesosphere.cosmos.storage.v1.circe

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.MediaTypedDecoder
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.storage.PackageQueueContents

object MediaTypedDecoders {
  implicit val repositoryListDecoder: MediaTypedDecoder[List[PackageRepository]] =
    MediaTypedDecoder[List[PackageRepository]](MediaTypes.RepositoryList)

  implicit val packageQueueContentsDecoder: MediaTypedDecoder[PackageQueueContents] =
    MediaTypedDecoder[PackageQueueContents](MediaTypes.PackageQueueContents)
}
