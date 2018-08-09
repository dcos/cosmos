package com.mesosphere.cosmos.rpc.v2.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class ServiceUpdateRequest(
   appId: AppId,
   packageName: Option[String],
   packageVersion: Option[universe.v3.model.Version] = None,
   options: Option[JsonObject],
   replace: Boolean,
   managerId: Option[String]
 )

object ServiceUpdateRequest {
  implicit val encode: Encoder[ServiceUpdateRequest] = deriveEncoder
  implicit val decode: Decoder[ServiceUpdateRequest] = deriveDecoder
  implicit val mediaTypedEncoder: MediaTypedEncoder[ServiceUpdateRequest] =
    MediaTypedEncoder(MediaTypes.V2ServiceUpdateRequest)
  implicit val mediaTypedRequestDecoder: MediaTypedRequestDecoder[ServiceUpdateRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.V2ServiceUpdateRequest))
}
