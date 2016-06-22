package com.mesosphere.cosmos.label.v1.circe

import com.mesosphere.cosmos.label
import com.mesosphere.universe.v2.circe.Decoders._
import io.circe.Decoder
import io.circe.generic.semiauto._

object Decoders {

  implicit val decodeLabelV1PackageMetadata: Decoder[label.v1.model.PackageMetadata] = {
    deriveFor[label.v1.model.PackageMetadata].decoder
  }

}
