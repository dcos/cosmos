package com.mesosphere.cosmos.label.v1.circe

import com.mesosphere.cosmos.label
import com.mesosphere.universe.v2.circe.Encoders._
import io.circe.Encoder
import io.circe.generic.semiauto._

object Encoders {

  implicit val encodeLabelV1PackageMetadata: Encoder[label.v1.model.PackageMetadata] = {
    deriveFor[label.v1.model.PackageMetadata].encoder
  }

}
