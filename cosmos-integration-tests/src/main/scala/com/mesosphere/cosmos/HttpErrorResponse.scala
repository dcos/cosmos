package com.mesosphere.cosmos

import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.twitter.finagle.http.Status
import org.scalatest.exceptions.TestFailedException

final case class HttpErrorResponse(
  status: Status,
  errorResponse: ErrorResponse
) extends TestFailedException(
  _ => Some(
    s"Status: $status, ErrorResponse: $errorResponse"
  ),
  None,
  _ => 0,
  None
)
