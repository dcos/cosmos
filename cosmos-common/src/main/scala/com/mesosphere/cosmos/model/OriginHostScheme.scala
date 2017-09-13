package com.mesosphere.cosmos.model

case class OriginHostScheme(host: Option[String] = None,
  protocol: Option[String] = None,
  forwardedFor: Option[String] = None,
  forwardedPort: Option[String] = None)
