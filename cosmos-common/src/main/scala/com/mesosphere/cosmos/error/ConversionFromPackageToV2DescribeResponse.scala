package com.mesosphere.cosmos.error

import io.circe.JsonObject

final case class ConversionFromPackageToV2DescribeResponse() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "A v4 package cannot be converted into a v2 DescribeResponse"
}
