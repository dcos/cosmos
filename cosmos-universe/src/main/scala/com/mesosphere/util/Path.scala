package com.mesosphere.util

trait Path {

  type Self <: Path

  def resolve(tail: RelativePath): Self  // scalastyle:ignore method.name

  def elements: List[String]

}

object Path {
  val Separator: String = "/"
}

final case class RelativePath private(override val elements: List[String]) extends Path {

  override type Self = RelativePath

  // scalastyle:off method.name
  override def resolve(tail: RelativePath): RelativePath = RelativePath(elements ++ tail.elements)
  // scalastyle:on method.name

  override def toString: String = elements.mkString(Path.Separator)

}

object RelativePath {

  def apply(path: String): RelativePath = validate(path).fold(throw _, identity)

  def validate(path: String): Either[Error, RelativePath] = {
    if (path.isEmpty) Left(Empty)
    else if (path.startsWith(Path.Separator)) Left(Absolute)
    else Right(RelativePath(path.split(Path.Separator).toList.filter(_.nonEmpty)))
  }

  sealed abstract class Error(override val getMessage: String) extends Exception
  case object Empty extends Error("Empty relative path")
  case object Absolute extends Error("Expected relative path, but found absolute path")

}

final case class AbsolutePath private(private val path: Option[RelativePath]) extends Path {

  override type Self = AbsolutePath

  // scalastyle:off method.name
  override def resolve(tail: RelativePath): AbsolutePath = {
    AbsolutePath(Some(path.fold(tail)(_.resolve(tail))))
  }
  // scalastyle:on method.name

  override def toString: String = Path.Separator + path.fold("")(_.toString)

  def elements: List[String] = path.fold(List.empty[String])(_.elements)

}

object AbsolutePath {

  val Root: AbsolutePath = AbsolutePath(Path.Separator)

  def apply(path: String): AbsolutePath = validate(path).fold(throw _, identity)

  def validate(path: String): Either[AbsolutePath.Error, AbsolutePath] = {
    if (path.isEmpty) Left(Empty)
    else if (!path.startsWith(Path.Separator)) Left(Relative)
    else {
      RelativePath.validate(path.drop(1)) match {
        case Right(relativePath) => Right(AbsolutePath(Some(relativePath)))
        case Left(RelativePath.Empty) => Right(AbsolutePath(None))
        case Left(RelativePath.Absolute) => Left(BadRoot)
      }
    }
  }

  sealed abstract class Error(override val getMessage: String) extends Exception
  case object Empty extends Error("Empty absolute path")
  case object Relative extends Error("Expected absolute path, but found relative path")
  case object BadRoot extends Error("Too many leading separators for absolute path")

}
