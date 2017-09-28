package com.mesosphere.cosmos.http

case class RequestSession(
  authorization: Option[Authorization],
  originInfo: OriginHostScheme,
  contentType: Option[MediaType] = None
)
