package com.mesosphere.util

final case class RelativePath private(override val elements: Vector[String]) extends Path {

  override type Self = RelativePath

  // scalastyle:off method.name
  def /(last: String): RelativePath = {
    last match {
      case "" =>
        throw new IllegalArgumentException("Empty path element")
      case _ if last.contains(Path.Separator) =>
        throw new IllegalArgumentException("Path element contains separator")
      case _ =>
        RelativePath(elements :+ last)
    }
  }
  // scalastyle:on method.name

  override def resolve(tail: RelativePath): RelativePath = RelativePath(elements ++ tail.elements)

  override def toString: String = elements.mkString(Path.Separator)

}

object RelativePath {

  val Empty: RelativePath = RelativePath(Vector.empty)

  def apply(path: String): RelativePath = validate(path).fold(throw _, identity)

  def validate(path: String): Either[Error, RelativePath] = {
    if (path.startsWith(Path.Separator)) Left(Absolute)
    else Right(RelativePath(path.split(Path.Separator).toVector.filter(_.nonEmpty)))
  }

  /**
   * The relative path that resolves to `path` at `base`.
   *
   * @throws IllegalArgumentException if `base` is not a parent of, or identical to, `path`.
   */
  def relativize(path: AbsolutePath, base: AbsolutePath): RelativePath = {
    // Take advantage of short-circuiting to avoid traversing the elements
    val valid = base.elements.size <= path.elements.size && path.elements.startsWith(base.elements)

    if (valid) RelativePath(path.elements.drop(base.elements.size))
    else throw new IllegalArgumentException("Paths cannot be relativized")
  }

  sealed abstract class Error(override val getMessage: String) extends Exception
  case object Absolute extends Error("Expected relative path, but found absolute path")

}
