package com.mesosphere.cosmos

import io.circe._

case class ErrorResponse(errors: List[ErrorResponseEntry])
case class ErrorResponseEntry(
  `type`: String,
  message: String,
  data: JsonObject = JsonObject.empty
)

object ErrorResponse {
  def apply(singleError: ErrorResponseEntry): ErrorResponse = {
    new ErrorResponse(List(singleError))
  }

  def apply(`type`: String, message: String, data: JsonObject = JsonObject.empty): ErrorResponse = {
    apply(ErrorResponseEntry(`type`, message, data))
  }

}
