package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.common.circe.Encoders._
import com.netaporter.uri.Uri
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryAddRequest(name: String, uri: Uri, index: Option[Int] = None)

object PackageRepositoryAddRequest {
  implicit val encoder: Encoder[PackageRepositoryAddRequest] = deriveEncoder
}
