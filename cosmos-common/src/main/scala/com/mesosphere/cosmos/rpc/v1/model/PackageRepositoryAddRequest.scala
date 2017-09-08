package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.circe.Decoders.decodeUri
import com.mesosphere.cosmos.circe.Encoders.encodeUri
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryAddRequest(name: String, uri: Uri, index: Option[Int] = None)

object PackageRepositoryAddRequest {
  implicit val encoder: Encoder[PackageRepositoryAddRequest] = deriveEncoder
  implicit val decoder: Decoder[PackageRepositoryAddRequest] = deriveDecoder
  implicit val packageRepositoryAddDecoder: MediaTypedRequestDecoder[PackageRepositoryAddRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.PackageRepositoryAddRequest))
}
