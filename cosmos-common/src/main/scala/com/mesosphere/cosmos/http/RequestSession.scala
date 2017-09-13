package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.model.OriginHostScheme

case class RequestSession(
  authorization: Option[Authorization],
  contentType: Option[MediaType] = None,
  originInfo: Option[OriginHostScheme] = None
)
