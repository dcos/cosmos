package com.mesosphere

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

package object util {

  implicit final class PathInterpolations(val sc: StringContext) extends AnyVal {

    def abspath(args: Any*): AbsolutePath = macro PathInterpolations.abspathMacro

  }

  object PathInterpolations {

    def abspathMacro(c: Context)(args: c.Expr[Any]*): c.Expr[AbsolutePath] = {
      import c.universe._

      // Examine the code of the object that abspath() was called on
      // Should have the form: PathInterpolations(StringContext(<string literal>))
      c.prefix.tree match {
        case Apply(_, List(Apply(_, List(Literal(Constant(rawPath: String)))))) =>
          AbsolutePath(rawPath) match {
            case Right(_) => c.Expr[AbsolutePath](q"AbsolutePath($rawPath).right.get")
            case Left(error) => c.abort(c.enclosingPosition, error.message)
          }
        case _ if args.nonEmpty =>
          c.abort(c.enclosingPosition, "Interpolated values are not supported")
        case _ =>
          c.abort(c.enclosingPosition, "This interpolator only works on string literals")
      }
    }

  }

}
