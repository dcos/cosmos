package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.rpc.MediaTypes
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class UninstallResponse(results: List[UninstallResult])

object UninstallResponse {
  implicit val encodeUninstallResponse: Encoder[UninstallResponse] = deriveEncoder[UninstallResponse]
  implicit val decodeUninstallResponse: Decoder[UninstallResponse] = deriveDecoder[UninstallResponse]
  implicit val packageUninstallEncoder: DispatchingMediaTypedEncoder[UninstallResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.UninstallResponse)
}
