package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class CapabilitiesResponse(capabilities: List[Capability])

object CapabilitiesResponse {
  implicit val encodeCapabilitiesResponse: Encoder[CapabilitiesResponse] =
    deriveEncoder[CapabilitiesResponse]
  implicit val decodeCapabilitiesResponse: Decoder[CapabilitiesResponse] =
    deriveDecoder[CapabilitiesResponse]
  implicit val capabilitiesEncoder: DispatchingMediaTypedEncoder[CapabilitiesResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.CapabilitiesResponse)
}
