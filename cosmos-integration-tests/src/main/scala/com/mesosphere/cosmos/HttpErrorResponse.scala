package com.mesosphere.cosmos

import com.twitter.finagle.http.Status
import org.scalatest.exceptions.TestFailedException

final case class HttpErrorResponse(
  status: Status,
  errorResponse: rpc.v1.model.ErrorResponse
) extends TestFailedException(
  messageFun = _ => Some(s"Status: $status, ErrorResponse: $errorResponse"),
  cause = None,
  failedCodeStackDepthFun = _ => 0,
  payload = None
)
