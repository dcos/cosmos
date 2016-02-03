package com.mesosphere.universe

case class PackagingVersion private(override val toString: String) extends AnyVal
object PackagingVersion {
  def validated(version: String): PackagingVersion = {
    assert(version == "2.0")
    new PackagingVersion(version)
  }
}
