package com.mesosphere.cosmos.http

case class RequestSession(
  authorization: Option[Authorization],
  contentType: Option[MediaType] = None
)
