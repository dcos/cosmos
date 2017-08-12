package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.common.circe.Encoders._
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryAddRequest(name: String, uri: Uri, index: Option[Int] = None)

object PackageRepositoryAddRequest {
  implicit val encoder: Encoder[PackageRepositoryAddRequest] = deriveEncoder
  implicit val decoder: Decoder[PackageRepositoryAddRequest] = deriveDecoder
}
