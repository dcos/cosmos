package com.mesosphere.universe.bijection

import scala.util.{Failure, Success}

object BijectionUtils {
  def twitterTryToScalaTry[A](a: com.twitter.util.Try[A]): scala.util.Try[A] = {
    a match {
      case com.twitter.util.Return(aaa) => Success(aaa)
      case com.twitter.util.Throw(e) => Failure(e)
    }
  }
}
