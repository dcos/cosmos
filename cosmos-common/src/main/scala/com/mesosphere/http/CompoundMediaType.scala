package com.mesosphere.http

import scala.util.Try

case class CompoundMediaType(mediaTypes: Set[MediaType]) {
  val show: String = {
    mediaTypes.map(_.show).mkString(",")
  }

  def combine(that: CompoundMediaType): CompoundMediaType = {
    CompoundMediaType(mediaTypes ++ that.mediaTypes)
  }
}

object CompoundMediaType {
  val empty = new CompoundMediaType(Set.empty)

  def apply(mt: MediaType): CompoundMediaType = {
    new CompoundMediaType(Set(mt))
  }

  def apply(mediaTypes: MediaType*): CompoundMediaType = {
    new CompoundMediaType(mediaTypes.toSet)
  }

  def parse(s: String): Try[CompoundMediaType] = {
    CompoundMediaTypeParser.parse(s)
  }

  implicit final class CompoundMediaTypeOps(val cmt: CompoundMediaType) extends AnyVal {
    def getMostAppropriateMediaType(mts: Set[MediaType]): Option[MediaType] = {
      calculateIntersectionAndOrder(cmt, mts).headOption
    }
  }

  def calculateIntersectionAndOrder(
    lhs: CompoundMediaType,
    rhs: Set[MediaType]
  ): List[MediaType] = {
    lhs.mediaTypes
      // If a MediaType is defined with a qvalue of 0.0 then it is explicitly saying that media type should never be
      // returned.  Here we remove it from the left-hand side of the intersection calculation so that even if it
      // exists in the right-hand side it will never be selected
      .filterNot(_.qValue == QualityValue.zero)
      // convert to a list so sorting can take place
      .toList
      // sort the left-hand side
      .sorted
      // remove the qvalue now that the left-hand side has been ranked
      .map(MediaType.copyWithoutQValue)
      // filter down to only those values that exist in the right-hand side
      .filter { mt =>
        rhs.exists(MediaType.compatible(mt, _))
      }
  }
}
