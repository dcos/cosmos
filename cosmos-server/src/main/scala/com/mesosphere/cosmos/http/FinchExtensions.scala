package com.mesosphere.cosmos.http

import cats.data.Xor
import com.twitter.util.Future
import io.finch.{DecodeRequest, Error, RequestReader, ValidationRule}

object FinchExtensions {

  def beTheExpectedType(expected: MediaType): ValidationRule[MediaType] =
    ValidationRule(s"match ${expected.show}") { actual =>
      MediaTypeOps.compatible(expected, actual)
    }

  implicit val decodeMediaType: DecodeRequest[MediaType] = {
    DecodeRequest.instance(s => MediaType.parse(s))
  }

  implicit class RequestReaderOps[A](val rr: RequestReader[A]) extends AnyVal {

    /** Like [[io.finch.RequestReader.map]] but with the possibility of failure.
      *
      * @param fn Converts `A` to `B`, or fails with a `String` error message
      */
    def convert[B](fn: A => String Xor B): RequestReader[B] = {
      rr.embedFlatMap { a =>
        fn(a) match {
          case Xor.Right(b) => Future.value(b)
          case Xor.Left(error) => Future.exception(Error.NotValid(rr.item, error))
        }
      }
    }

  }

}
