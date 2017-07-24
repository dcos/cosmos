package com.mesosphere.cosmos

import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.twitter.finagle.http.Status
import org.scalatest.exceptions.TestFailedException

case class HttpErrorResponse(
  status: Status,
  errorResponse: ErrorResponse
) extends TestFailedException(
  _ => Some(
    s"${errorResponse.`type`} $status: ${errorResponse.message}"
  ),
  None,
  _ => 0,
  None
)
