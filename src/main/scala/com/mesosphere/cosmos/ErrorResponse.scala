package com.mesosphere.cosmos

import io.circe._

case class ErrorResponse(
  `type`: String,
  message: String,
  data: Option[JsonObject] = None
)
