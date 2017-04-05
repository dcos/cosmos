package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo
  */
case class Repository(packages: List[PackageDefinition])

object Repository {
  implicit val decodeRepository: Decoder[Repository] = deriveDecoder[Repository]
  implicit val encodeRepository: Encoder[Repository] = deriveEncoder[Repository]
}
