package com.mesosphere.cosmos.internal.circe

import com.mesosphere.cosmos.internal.model.{BundleDefinition, V2Bundle, V3Bundle}
import com.mesosphere.cosmos.rpc.v1.model.{PublishRequest, PublishResponse}
import com.mesosphere.universe.v3.circe.Encoders._
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {
  implicit val encodeBundleDefinition: Encoder[BundleDefinition] =
    Encoder.instance {
      case v2: V2Bundle => v2.asJson
      case v3: V3Bundle => v3.asJson
    }
  implicit val encodeV2Bundle: Encoder[V2Bundle] = deriveEncoder[V2Bundle]
  implicit val encodeV3Bundle: Encoder[V3Bundle] = deriveEncoder[V3Bundle]

  implicit val encodePublishRequest: Encoder[PublishRequest] = deriveEncoder[PublishRequest]
  implicit val encodePublishResponse: Encoder[PublishResponse] = deriveEncoder[PublishResponse]

}
