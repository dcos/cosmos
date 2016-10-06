package com.mesosphere.universe.bijection

import scala.util.{Failure, Success}

object BijectionUtils {
  def twitterTryToScalaTry[A, B](a: (A) => com.twitter.util.Try[B]): (A) => scala.util.Try[B] = {
    (aa: A) => a(aa) match {
      case com.twitter.util.Return(aaa) => Success(aaa)
      case com.twitter.util.Throw(e) => Failure(e)
    }
  }
}
