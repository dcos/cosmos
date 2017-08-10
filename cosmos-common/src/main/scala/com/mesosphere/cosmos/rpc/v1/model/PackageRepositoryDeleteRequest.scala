package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.common.circe.Encoders._
import com.netaporter.uri.Uri
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class PackageRepositoryDeleteRequest(name: Option[String] = None, uri: Option[Uri] = None)

object PackageRepositoryDeleteRequest {
  implicit val encoder: Encoder[PackageRepositoryDeleteRequest] = deriveEncoder
}
