package com.mesosphere.cosmos.http

import com.mesosphere.http.MediaType
import com.mesosphere.http.OriginHostScheme

case class RequestSession(
  authorization: Option[Authorization],
  originInfo: OriginHostScheme,
  contentType: Option[MediaType] = None
)
