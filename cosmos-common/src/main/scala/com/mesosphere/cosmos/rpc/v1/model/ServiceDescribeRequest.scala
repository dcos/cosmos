package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class ServiceDescribeRequest(
  appId: AppId,
  managerId: Option[String],
  packageName: Option[String],
  packageVersion: Option[universe.v3.model.Version]
)

object ServiceDescribeRequest {
  implicit val encode: Encoder[ServiceDescribeRequest] = deriveEncoder
  implicit val decode: Decoder[ServiceDescribeRequest] = deriveDecoder
  implicit val mediaTypedEncoder: MediaTypedEncoder[ServiceDescribeRequest] =
    MediaTypedEncoder(MediaTypes.ServiceDescribeRequest)
  implicit val mediaTypedRequestDecoder: MediaTypedRequestDecoder[ServiceDescribeRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.ServiceDescribeRequest))
}
