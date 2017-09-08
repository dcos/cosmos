package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class SearchResult(
  name: String,
  currentVersion: universe.v3.model.Version,
  versions: Map[universe.v3.model.Version, universe.v3.model.ReleaseVersion],
  description: String,
  framework: Boolean,
  tags: List[universe.v3.model.Tag],
  selected: Option[Boolean] = None,
  images: Option[universe.v3.model.Images] = None
)

object SearchResult {
  implicit val encodeSearchResult: Encoder[SearchResult] = deriveEncoder[SearchResult]
  implicit val decodeSearchResult: Decoder[SearchResult] = deriveDecoder[SearchResult]
}
