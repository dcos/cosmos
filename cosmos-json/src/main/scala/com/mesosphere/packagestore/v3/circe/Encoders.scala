package com.mesosphere.packagestore.v3.circe

import com.mesosphere.packagestore.v3.model._
import com.mesosphere.universe.v3.circe.Encoders._
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {
  implicit val encodeV2PackageBundle: Encoder[V2PackageBundle] = deriveFor[V2PackageBundle].encoder
  implicit val encodeV3PackageBundle: Encoder[V3PackageBundle] = deriveFor[V3PackageBundle].encoder
  implicit val encodePackageBundle: Encoder[PackageBundle] =
    Encoder.instance {
      case v2: V2PackageBundle => v2.asJson
      case v3: V3PackageBundle => v3.asJson
    }
}
