package com.mesosphere.cosmos.http

case class CompoundMediaType(mediaTypes: Set[MediaType]) {
  val show: String = {
    mediaTypes.map(_.show).mkString(",")
  }
}
object CompoundMediaType {
  val empty = new CompoundMediaType(Set.empty)

  def apply(mt: MediaType): CompoundMediaType = {
    new CompoundMediaType(Set(mt))
  }
}
