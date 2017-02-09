package com.mesosphere.util

trait Path

object Path {
  val Root: AbsolutePath = AbsolutePath()
  val Separator: String = "/"
}

// TODO cruhland
final case class AbsolutePath() extends Path {

  // scalastyle:off method.name
  def /(p: RelativePath): AbsolutePath = ???

  def /(p: String): AbsolutePath = ???
  // scalastyle:on method.name

}

final case class RelativePath private(private val elements: List[String]) extends Path {

  // scalastyle:off method.name
  def /(p: RelativePath): RelativePath = RelativePath(elements ++ p.elements)
  // scalastyle:on method.name

  override def toString: String = elements.mkString(Path.Separator)

}

object RelativePath {

  def apply(path: String): Either[Error, RelativePath] = {
    if (path.isEmpty) Left(Empty)
    else if (path.startsWith(Path.Separator)) Left(Absolute)
    else Right(RelativePath(List(path)))
  }

  sealed trait Error
  case object Empty extends Error
  case object Absolute extends Error

}
