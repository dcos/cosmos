package com.mesosphere.universe.v4.model

import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

import scala.collection.immutable.Seq

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo
  */
case class Repository(packages: Seq[universe.v4.model.PackageDefinition])

object Repository {
  implicit val decodeRepository: Decoder[Repository] = deriveDecoder[Repository]
  implicit val encodeRepository: Encoder[Repository] = deriveEncoder[Repository]
}
