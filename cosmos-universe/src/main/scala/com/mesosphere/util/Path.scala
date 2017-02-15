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
