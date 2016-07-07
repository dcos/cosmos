package com.mesosphere.universe.v3.model

sealed abstract class PackagingVersion(val show: String)
case object V2PackagingVersion extends PackagingVersion("2.0")
case object V3PackagingVersion extends PackagingVersion("3.0")

object PackagingVersion {

  val allVersions: Map[String, PackagingVersion] = {
    Seq(V2PackagingVersion, V3PackagingVersion).map(v => v.show -> v).toMap
  }

}
