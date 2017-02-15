package com.mesosphere.util

/**
 * A location in a treelike structure where the nodes are named by strings.
 *
 * Useful for safely manipulating paths obtained from various storage systems such as the local
 * filesystem, ZooKeeper, and S3, as well as more abstract sources like URIs.
 */
trait Path {

  /** The concrete type of this path. */
  type Self <: Path

  /**
   * The path obtained by appending the given element to the end of this path.
   *
   * @throws IllegalArgumentException if the given element is empty or contains [[Path.Separator]].
   */
  def /(last: String): Self // scalastyle:ignore method.name

  /** The path obtained by appending the given relative path to the end of this path. */
  def resolve(tail: RelativePath): Self

  /** The node names in this path, ordered by increasing depth. */
  def elements: Vector[String]

}

object Path {
  val Separator: String = "/"
}
