package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class ServiceUpdateResponse(
  `package`: universe.v4.model.PackageDefinition,
  resolvedOptions: JsonObject,
  marathonDeploymentId: String
)

object ServiceUpdateResponse {
  implicit val encode: Encoder[ServiceUpdateResponse] = deriveEncoder
  implicit val decode: Decoder[ServiceUpdateResponse] = deriveDecoder
  implicit val mediaTypedEncoder: MediaTypedEncoder[ServiceUpdateResponse] =
    MediaTypedEncoder(MediaTypes.ServiceUpdateResponse)
  implicit val dispatchingMediaTypedEncoder: DispatchingMediaTypedEncoder[ServiceUpdateResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ServiceUpdateResponse)
}
