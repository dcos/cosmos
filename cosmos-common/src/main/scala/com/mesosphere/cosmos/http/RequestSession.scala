package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.model.OriginHostScheme

case class RequestSession(authorization: Option[Authorization],
  originInfo: OriginHostScheme,
  contentType: Option[MediaType] = None)
