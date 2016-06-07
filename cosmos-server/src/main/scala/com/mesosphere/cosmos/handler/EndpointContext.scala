package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.circe.MediaTypedEncoder
import com.mesosphere.cosmos.http.RequestSession

/** Information extracted from a request that affects endpoint behavior. */
case class EndpointContext[Request, Response](
  requestBody: Request,
  session: RequestSession,
  responseEncoder: MediaTypedEncoder[Response]
)
