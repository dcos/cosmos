package com.mesosphere.universe.bijection

import scala.util.{Failure, Success}

object BijectionUtils {
  def twitterTryToScalaTry[A](a: com.twitter.util.Try[A]): scala.util.Try[A] = {
    a match {
      case com.twitter.util.Return(aaa) => Success(aaa)
      case com.twitter.util.Throw(e) => Failure(e)
    }
  }

  def scalaTryToTwitterTry[A](a: scala.util.Try[A]): com.twitter.util.Try[A] = {
    a match {
      case scala.util.Success(aaa) => com.twitter.util.Return(aaa)
      case scala.util.Failure(e) => com.twitter.util.Throw(e)
    }
  }
}
