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

case class ServiceDescribeResponse(
  `package`: universe.v4.model.PackageDefinition,
  upgradesTo: List[universe.v3.model.Version],
  downgradesTo: List[universe.v3.model.Version],
  resolvedOptions: Option[JsonObject],
  userProvidedOptions: Option[JsonObject]
)

object ServiceDescribeResponse {
  implicit val encode: Encoder[ServiceDescribeResponse] = deriveEncoder
  implicit val decode: Decoder[ServiceDescribeResponse] = deriveDecoder
  implicit val mediaTypedEncoder: MediaTypedEncoder[ServiceDescribeResponse] =
    MediaTypedEncoder(MediaTypes.ServiceDescribeResponse)
  implicit val dispatchingMediaTypedEncoder: DispatchingMediaTypedEncoder[ServiceDescribeResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ServiceDescribeResponse)
}
