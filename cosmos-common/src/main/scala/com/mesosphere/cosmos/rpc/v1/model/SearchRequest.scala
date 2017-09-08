package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class SearchRequest(query: Option[String])

object SearchRequest {
  implicit val encodeSearchRequest: Encoder[SearchRequest] = deriveEncoder[SearchRequest]
  implicit val decodeSearchRequest: Decoder[SearchRequest] = deriveDecoder[SearchRequest]
  implicit val packageSearchDecoder: MediaTypedRequestDecoder[SearchRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.SearchRequest))
}
