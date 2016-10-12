package com.mesosphere.cosmos.internal.circe

import com.mesosphere.cosmos.internal.model.{BundleDefinition, V2Bundle, V3Bundle}
import com.mesosphere.cosmos.rpc.v1.model.{PublishRequest, PublishResponse}
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.model.{PackagingVersion, V2PackagingVersion, V3PackagingVersion}
import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto._

object Decoders {

  implicit val decodeBundleDefinition: Decoder[BundleDefinition] = {
    Decoder.instance { (hc: HCursor) =>
      hc.downField("packagingVersion").as[PackagingVersion].flatMap {
        case V2PackagingVersion => hc.as[V2Bundle]
        case V3PackagingVersion => hc.as[V3Bundle]
      }
    }
  }
  implicit val decodeV2Bundle = deriveDecoder[V2Bundle]
  implicit val decodeV3Bundle = deriveDecoder[V3Bundle]

  implicit val decodePublishRequest: Decoder[PublishRequest] = deriveDecoder[PublishRequest]
  implicit val decodePublishResponse: Decoder[PublishResponse] = deriveDecoder[PublishResponse]

}
