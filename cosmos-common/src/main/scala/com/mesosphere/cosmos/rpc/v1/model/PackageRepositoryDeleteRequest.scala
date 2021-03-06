package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.circe.Decoders.decodeUri
import com.mesosphere.cosmos.circe.Encoders.encodeUri
import io.lemonlabs.uri.Uri
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryDeleteRequest(name: Option[String] = None, uri: Option[Uri] = None)

object PackageRepositoryDeleteRequest {
  implicit val encoder: Encoder[PackageRepositoryDeleteRequest] = deriveEncoder
  implicit val decoder: Decoder[PackageRepositoryDeleteRequest] = deriveDecoder
  implicit val packageRepositoryDeleteDecoder: MediaTypedRequestDecoder[PackageRepositoryDeleteRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.PackageRepositoryDeleteRequest))
}
