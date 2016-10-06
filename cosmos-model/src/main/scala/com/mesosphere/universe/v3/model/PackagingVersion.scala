package com.mesosphere.universe.v3.model

import com.twitter.util.{Return, Throw, Try}

sealed abstract class PackagingVersion private[model](val show: String)
case object V2PackagingVersion extends PackagingVersion("2.0")
case object V3PackagingVersion extends PackagingVersion("3.0")

object PackagingVersion {

  val allVersions = Seq(V2PackagingVersion, V3PackagingVersion)

  private[this] val allVersionsString = allVersions.map(_.show).mkString(", ")

  private[this] val allVersionsIndex: Map[String, PackagingVersion] = {
    allVersions.map(v => v.show -> v).toMap
  }

  def apply(s: String): Try[PackagingVersion] = {
    allVersionsIndex.get(s) match {
      case Some(v) => Return(v)
      case _ => Throw(new IllegalArgumentException(
        s"Expected one of [$allVersionsString] for packaging version, but found [$s]"
      ))
    }
  }

}
