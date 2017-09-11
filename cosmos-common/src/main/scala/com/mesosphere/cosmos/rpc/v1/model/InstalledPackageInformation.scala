package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.v2.model.Resource
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class InstalledPackageInformation(
  packageDefinition: InstalledPackageInformationPackageDetails,
  resourceDefinition: Option[Resource] = None
)

object InstalledPackageInformation {
  implicit val encodePackageInformation: Encoder[InstalledPackageInformation] = deriveEncoder[InstalledPackageInformation]
  implicit val decodePackageInformation: Decoder[InstalledPackageInformation] = deriveDecoder[InstalledPackageInformation]
}
