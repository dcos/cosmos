package com.mesosphere.http

import cats.instances.list._
import cats.instances.try_._
import cats.syntax.traverse._
import scala.util.Success
import scala.util.Try

object CompoundMediaTypeParser {

  def parse(s: String): Try[CompoundMediaType] = {
    s.split(',').toList.filterNot(_.trim.isEmpty) match {
      case Nil => Success(new CompoundMediaType(Set.empty))
      case mts =>
        mts.map(MediaType.parse)
          .sequence
          .map { mediaTypes =>
            CompoundMediaType(backfillParams(mediaTypes)._2.toSet)
          }
    }
  }

  /**
    * This method implements the logic necessary to "propagate parameters" to all MediaTypes specified as part of
    * an Accept header. Since we have a concrete type that represents Media Type we need to "back fill" the parameters
    * specified on media types that come later in the string to those earlier in the string where those earlier
    * media types do not have parameters defined.
    *
    *
    * For example, given the Accept header value of `* application/xml;q=0.8,application/json,application/x-protobuf;q=0.9`
    * We will end up with three media types:
    * * application/xml;q=0.8
    * * application/json;q=0.9
    * * application/x-protobuf;q=0.9
    *
    * @see https://tools.ietf.org/html/rfc7231#section-5.3.2 for full details on the spec for the Accept header
    * and content negotiation.
    */
  private[this] def backfillParams(
    mts: List[MediaType]
  ): (Map[String, String], List[MediaType]) = {
    mts match {
      case Nil => (Map.empty, Nil)
      case x :: xs =>
        // walk down the list of media types
        backfillParams(xs) match {
          case (m, l) =>
            if (x.parameters.isEmpty) {
              // if the current media type doesn't have parameters set on it set it to be the parameters returned
              // from the next media type in the list
              m -> (x.copy(parameters = m) :: l)
            } else {
              // if the current media type does have parameters set on it, leave them intact and pass
              // up for previous media type in the list
              x.parameters -> (x :: l)
            }
        }
    }
  }

}
