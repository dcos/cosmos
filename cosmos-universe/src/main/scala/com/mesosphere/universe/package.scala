package com.mesosphere

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

package object universe {

  type AbsolutePath = String

  implicit final class PathInterpolations(val sc: StringContext) extends AnyVal {

    def abspath(args: Any*): AbsolutePath = macro PathInterpolations.abspathMacro

  }

  object PathInterpolations {

    def abspathMacro(c: Context)(args: c.Expr[Any]*): c.Expr[AbsolutePath] = {
      import c.universe._

      // Examine the code of the object that abspath() was called on
      // Should have the form: PathInterpolations(StringContext(<string literal>))
      c.prefix.tree match {
        case Apply(_, List(Apply(_, List(path @ Literal(Constant(rawPath: String))))))
          if rawPath.nonEmpty =>
          c.Expr(path)
        case _ =>
          c.abort(c.enclosingPosition, "Invalid path")
      }
    }

  }

}
