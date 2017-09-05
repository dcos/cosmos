package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class DescribeRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion]
)

object DescribeRequest {
  implicit val encodeDescribeRequest: Encoder[DescribeRequest] = deriveEncoder[DescribeRequest]
  implicit val decodeDescribeRequest: Decoder[DescribeRequest] = deriveDecoder[DescribeRequest]
  implicit val packageDescribeDecoder: MediaTypedRequestDecoder[DescribeRequest] =
    MediaTypedRequestDecoder(MediaTypedDecoder(MediaTypes.DescribeRequest))
}
