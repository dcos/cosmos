package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.http.MediaTypeOps._

class CompoundMediaTypeOps(val cmt: CompoundMediaType) extends AnyVal {

  def getMostAppropriateMediaType(mts: Set[MediaType]): Option[MediaType] = {
    CompoundMediaTypeOps.calculateIntersectionAndOrder(cmt, mts).headOption
  }

}

object CompoundMediaTypeOps {
  import scala.language.implicitConversions
  import MediaTypeOps.mediaTypeOrdering

  implicit def compoundMediaTypeToCompoundMediaTypeOps(cmt: CompoundMediaType): CompoundMediaTypeOps = {
    new CompoundMediaTypeOps(cmt)
  }

  def calculateIntersectionAndOrder(lhs: CompoundMediaType, rhs: Set[MediaType]): List[MediaType] = {
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
      .map(MediaTypeOps.copyWithoutQValue)
      // filter down to only those values that exist in the right-hand side
      .filter { mt =>
        rhs.exists(MediaTypeOps.compatible(mt, _))
      }
  }
}
