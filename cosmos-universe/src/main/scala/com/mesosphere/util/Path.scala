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

  sealed trait Error
  case object Empty extends Error
  case object Absolute extends Error

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

  sealed trait Error
  case object Empty extends Error
  case object Relative extends Error
  case object BadRoot extends Error

}
