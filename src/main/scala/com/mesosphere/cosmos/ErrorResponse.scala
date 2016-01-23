package com.mesosphere.cosmos

import io.circe._

case class ErrorResponse(errors: List[ErrorResponseEntry])
case class ErrorResponseEntry(
  `type`: String,
  message: String,
  data: Json = Json.obj()
)

object ErrorResponse {
  def apply(singleError: ErrorResponseEntry): ErrorResponse = {
    new ErrorResponse(List(singleError))
  }

  def apply(`type`: String, message: String, data: Json = Json.obj()): ErrorResponse = {
    apply(ErrorResponseEntry(`type`, message, data))
  }

}
