package org.apache.curator.framework

import com.mesosphere.util.AbsolutePath

package object api {

  implicit final class PathableOps[A](val underlying: Pathable[A]) extends AnyVal {
    def forPath(path: AbsolutePath): A = underlying.forPath(path.toString)
  }

  implicit final class PathAndBytesableOps[A](val underlying: PathAndBytesable[A]) extends AnyVal {
    def forPath(path: AbsolutePath): A = underlying.forPath(path.toString)
    def forPath(path: AbsolutePath, data: Array[Byte]): A = underlying.forPath(path.toString, data)
  }

}
