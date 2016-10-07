package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.RequestSession

/** Information extracted from a request that affects endpoint behavior. */
case class EndpointContext[Request, Response](
  requestBody: Request,
  session: RequestSession,
  responseEncoder: MediaTypedEncoder[Response]
)
