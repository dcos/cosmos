package com.mesosphere.universe

case class Platforms(
  windows: Option[Architectures],
  linux: Option[Architectures],
  darwin: Option[Architectures]
)
