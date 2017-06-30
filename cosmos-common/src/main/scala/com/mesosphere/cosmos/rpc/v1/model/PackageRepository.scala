package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.common.circe.Encoders._
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._

case class PackageRepository(name: String, uri: Uri)

object PackageRepository {

  implicit val decoder: Decoder[PackageRepository] = deriveDecoder
  implicit val encoder: Encoder[PackageRepository] = deriveEncoder

  implicit val repositoryListDecoder: MediaTypedDecoder[List[PackageRepository]] = {
    MediaTypedDecoder[List[PackageRepository]](MediaTypes.RepositoryList)
  }

  implicit val repositoryListEncoder: MediaTypedEncoder[List[PackageRepository]] = {
    MediaTypedEncoder[List[PackageRepository]](MediaTypes.RepositoryList)
  }

}
