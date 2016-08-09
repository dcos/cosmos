package com.mesosphere.packagestore.v3.circe


import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.packagestore.v3.model._
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.model._
import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto._

object Decoders {
  implicit val decodeV2PackageBundle = deriveFor[V2PackageBundle].decoder
  implicit val decodeV3PackageBundle = deriveFor[V3PackageBundle].decoder
  implicit val decodePackageBundle: Decoder[PackageBundle] = {
    Decoder.instance { (hc: HCursor) =>
      hc.downField("packagingVersion").as[PackagingVersion].flatMap {
        case V2PackagingVersion => hc.as[V2PackageBundle]
        case V3PackagingVersion => hc.as[V3PackageBundle]
      }
    }
  }
}
