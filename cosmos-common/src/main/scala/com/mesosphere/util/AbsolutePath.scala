package com.mesosphere.util

final case class AbsolutePath private(private val path: RelativePath) extends Path {

  override type Self = AbsolutePath

  def /(last: String): AbsolutePath = AbsolutePath(path / last) // scalastyle:ignore method.name

  override def resolve(tail: RelativePath): AbsolutePath = AbsolutePath(path.resolve(tail))

  /**
   * The relative path that resolves to this path at `base`.
   *
   * @throws IllegalArgumentException if `base` is not a parent of, or identical to, this path.
   */
  def relativize(base: AbsolutePath): RelativePath = RelativePath.relativize(this, base)

  override def toString: String = Path.Separator + path.toString

  def elements: Vector[String] = path.elements

}

object AbsolutePath {

  val Root: AbsolutePath = AbsolutePath(RelativePath.Empty)

  def apply(path: String): AbsolutePath = validate(path).fold(throw _, identity)

  def validate(path: String): Either[AbsolutePath.Error, AbsolutePath] = {
    if (path.isEmpty) Left(Empty)
    else if (!path.startsWith(Path.Separator)) Left(Relative)
    else {
      RelativePath.validate(path.drop(1)) match {
        case Right(relativePath) => Right(AbsolutePath(relativePath))
        case Left(RelativePath.Absolute) => Left(BadRoot)
      }
    }
  }

  sealed abstract class Error(override val getMessage: String) extends Exception
  case object Empty extends Error("Empty absolute path")
  case object Relative extends Error("Expected absolute path, but found relative path")
  case object BadRoot extends Error("Too many leading separators for absolute path")

}
