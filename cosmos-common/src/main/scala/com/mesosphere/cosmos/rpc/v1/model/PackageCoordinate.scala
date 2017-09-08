package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

final case class PackageCoordinate(name: String, version: universe.v3.model.Version)

object PackageCoordinate {
  implicit val encodePackageCoordinate: Encoder[PackageCoordinate] = deriveEncoder[PackageCoordinate]
  implicit val decodePackageCoordinate: Decoder[PackageCoordinate] = deriveDecoder[PackageCoordinate]
}
