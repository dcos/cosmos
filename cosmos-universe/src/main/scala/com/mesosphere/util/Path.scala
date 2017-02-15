package com.mesosphere.util

trait Path {

  type Self <: Path

  def /(last: String): Self // scalastyle:ignore method.name

  def resolve(tail: RelativePath): Self

  def elements: Vector[String]

}

object Path {
  val Separator: String = "/"
}

final case class RelativePath private(override val elements: Vector[String]) extends Path {

  import RelativePath._

  override type Self = RelativePath

  // scalastyle:off method.name
  def /(last: String): RelativePath = {
    validateElement(last)
    RelativePath(elements :+ last)
  }
  // scalastyle:on method.name

  override def resolve(tail: RelativePath): RelativePath = RelativePath(elements ++ tail.elements)

  override def toString: String = elements.mkString(Path.Separator)

}

object RelativePath {

  def apply(path: String): RelativePath = validate(path).fold(throw _, identity)

  def validate(path: String): Either[Error, RelativePath] = {
    if (path.isEmpty) Left(Empty)
    else if (path.startsWith(Path.Separator)) Left(Absolute)
    else Right(RelativePath(path.split(Path.Separator).toVector.filter(_.nonEmpty)))
  }

  def element(s: String): RelativePath = {
    validateElement(s)
    RelativePath(Vector(s))
  }

  private def validateElement(s: String): Unit = {
    s match {
      case "" =>
        throw new IllegalArgumentException("Empty path element")
      case _ if s.contains(Path.Separator) =>
        throw new IllegalArgumentException("Path element contains separator")
      case _ =>
        // Valid
    }
  }

  sealed abstract class Error(override val getMessage: String) extends Exception
  case object Empty extends Error("Empty relative path")
  case object Absolute extends Error("Expected relative path, but found absolute path")

}

final case class AbsolutePath private(private val path: Option[RelativePath]) extends Path {

  override type Self = AbsolutePath

  // scalastyle:off method.name
  def /(last: String): AbsolutePath = {
    AbsolutePath(Some(path.fold(RelativePath.element(last))(_ / last)))
  }
  // scalastyle:on method.name

  override def resolve(tail: RelativePath): AbsolutePath = {
    AbsolutePath(Some(path.fold(tail)(_.resolve(tail))))
  }

  override def toString: String = Path.Separator + path.fold("")(_.toString)

  def elements: Vector[String] = path.fold(Vector.empty[String])(_.elements)

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
