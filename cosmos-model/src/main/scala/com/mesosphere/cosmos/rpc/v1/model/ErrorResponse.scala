package com.mesosphere.cosmos.rpc.v1.model

import io.circe.JsonObject

case class ErrorResponse(
  `type`: String,
  message: String,
  data: Option[JsonObject] = None
)
