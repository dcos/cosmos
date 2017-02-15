package com.mesosphere.util

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