package com.mesosphere.universe.v4.model

import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Decoders._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo
  */
case class Repository(packages: List[universe.v4.model.PackageDefinition])

object Repository {
  implicit val decodeRepository: Decoder[Repository] = deriveDecoder[Repository]
  implicit val encodeRepository: Encoder[Repository] = deriveEncoder[Repository]
}
