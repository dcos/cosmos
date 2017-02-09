package com.mesosphere.util

trait Path

object Path {
  val Root: AbsolutePath = AbsolutePath()
}

// TODO cruhland
final case class AbsolutePath() extends Path {

  // scalastyle:off method.name
  def /(p: RelativePath): AbsolutePath = ???

  def /(p: String): AbsolutePath = ???
  // scalastyle:on method.name

}

final case class RelativePath(toList: List[String]) extends Path {

  // scalastyle:off method.name
  def /(p: RelativePath): RelativePath = RelativePath(toList ++ p.toList)
  // scalastyle:on method.name

}
