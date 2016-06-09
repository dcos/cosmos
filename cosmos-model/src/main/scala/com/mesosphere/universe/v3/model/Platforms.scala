package com.mesosphere.universe.v3.model

case class Platforms(
  windows: Option[Architectures],
  linux: Option[Architectures],
  darwin: Option[Architectures]
)
