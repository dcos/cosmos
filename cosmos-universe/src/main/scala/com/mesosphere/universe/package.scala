package com.mesosphere

package object universe {

  type AbsolutePath = String

  implicit final class PathInterpolations(val sc: StringContext) extends AnyVal {

    def abspath(args: Any*): AbsolutePath = sc.parts.mkString

  }

}
