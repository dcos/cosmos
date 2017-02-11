package com.mesosphere.util

trait Path {

  type Self <: Path

  def /(tail: RelativePath): Self  // scalastyle:ignore method.name

}

object Path {
  val Separator: String = "/"
}

final case class RelativePath private(private val elements: List[String]) extends Path {

  override type Self = RelativePath

  // scalastyle:off method.name
  override def /(tail: RelativePath): RelativePath = RelativePath(elements ++ tail.elements)
  // scalastyle:on method.name

  override def toString: String = elements.mkString(Path.Separator)

}

object RelativePath {

  def apply(path: String): Either[Error, RelativePath] = {
    if (path.isEmpty) Left(Empty)
    else if (path.startsWith(Path.Separator)) Left(Absolute)
    else Right(RelativePath(path.split(Path.Separator).toList.filter(_.nonEmpty)))
  }

  sealed abstract class Error(val message: String)
  case object Empty extends Error("Empty relative path")
  case object Absolute extends Error("Expected relative path, but found absolute path")

}

final case class AbsolutePath private(private val path: Option[RelativePath]) extends Path {

  override type Self = AbsolutePath

  // scalastyle:off method.name
  override def /(tail: RelativePath): AbsolutePath = AbsolutePath(Some(path.fold(tail)(_ / tail)))
  // scalastyle:on method.name

  override def toString: String = Path.Separator + path.fold("")(_.toString)

}

object AbsolutePath {

  def apply(path: String): Either[Error, AbsolutePath] = {
    if (path.isEmpty) Left(Empty)
    else if (!path.startsWith(Path.Separator)) Left(Relative)
    else {
      RelativePath(path.drop(1)) match {
        case Right(relativePath) => Right(AbsolutePath(Some(relativePath)))
        case Left(RelativePath.Empty) => Right(AbsolutePath(None))
        case Left(RelativePath.Absolute) => Left(BadRoot)
      }
    }
  }

  sealed abstract class Error(val message: String)
  case object Empty extends Error("Empty absolute path")
  case object Relative extends Error("Expected absolute path, but found relative path")
  case object BadRoot extends Error("Too many leading separators for absolute path")

}
