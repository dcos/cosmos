package com.mesosphere.universe.v3.model

sealed abstract class PackagingVersion

case class V2PackagingVersion(v: String) extends PackagingVersion {
  assert(v == "2.0", "Only packagingVersion 2.0 is supported")
}
object V2PackagingVersion {
  val instance = V2PackagingVersion("2.0")
}

case class V3PackagingVersion(v: String) extends PackagingVersion {
  assert(v == "3.0", "Only packagingVersion 3.0 is supported")
}
object V3PackagingVersion {
  val instance = V3PackagingVersion("3.0")
}
