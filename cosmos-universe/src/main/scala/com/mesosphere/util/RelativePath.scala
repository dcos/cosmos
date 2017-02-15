package com.mesosphere.util

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