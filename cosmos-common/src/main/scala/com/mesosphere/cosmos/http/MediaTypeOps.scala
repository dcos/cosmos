package com.mesosphere.cosmos.http

class MediaTypeOps(val mediaType: MediaType) extends AnyVal {
  def isCompatibleWith(other: MediaType): Boolean = {
    MediaTypeOps.compatible(mediaType, other)
  }

  /**
    * Behavior note: right now this function does not differentiate between
    *  * `mediaType` does not have a q parameter defined and
    *  * `mediaType` does have a q parameter defined but parsing failed
    */
  def qValue: QualityValue = {
    MediaTypeOps.qValue(mediaType)
  }
}

object MediaTypeOps {
  import scala.language.implicitConversions

  implicit def mediaTypeToMediaTypeOps(mt: MediaType): MediaTypeOps = {
    new MediaTypeOps(mt)
  }

  def compatibleIgnoringParameters(expected: MediaType, actual: MediaType): Boolean = {
    expected.`type` == actual.`type` && expected.subType == actual.subType
  }

  def compatible(expected: MediaType, actual: MediaType): Boolean = {
    compatibleIgnoringParameters(expected, actual) &&
      isLeftSubSetOfRight(
        stripPropertiesIgnoredDuringComparison(expected.parameters),
        stripPropertiesIgnoredDuringComparison(actual.parameters)
      )
  }

  /**
    * Behavior note: right now this function does not differentiate between
    *  * `mediaType` does not have a q parameter defined and
    *  * `mediaType` does have a q parameter defined but parsing failed
    */
  def qValue(mediaType: MediaType): QualityValue = {
    QualityValue.getFromMediaType(mediaType).toOption.flatten.getOrElse(QualityValue.default)
  }

  def copyWithoutQValue(mt: MediaType): MediaType = {
    mt.copy(parameters = mt.parameters - "q")
  }

  implicit val mediaTypeOrdering: Ordering[MediaType] = new Ordering[MediaType] {
    // note: does not currently account for '*' wild cards, since we require users to explicitly define their
    // media types it should be okay for us to ignore this scenario.
    override def compare(
      x: MediaType,
      y: MediaType
    ): Int = {
      val comparisons =
        // qvalues with the highest number come first, flip the sign here to get that cheaply
        -QualityValue.qualityValueOrdering.compare(x.qValue, y.qValue) #::
        Ordering.String.compare(x.`type`, y.`type`) #::
        Ordering.String.compare(x.subType.value, y.subType.value) #::
        Ordering.Option[String].compare(x.subType.suffix, y.subType.suffix) #::
        // the higher the number of parameters the higher priority it should be, flip the sign here to get that cheaply
        -Ordering.Int.compare(
          stripPropertiesIgnoredDuringComparison(x.parameters).size,
          stripPropertiesIgnoredDuringComparison(y.parameters).size) #::
        Stream.empty

      comparisons.find(_ != 0).getOrElse(0)
    }
  }

  private[this] def isLeftSubSetOfRight(left: Map[String, String], right: Map[String, String]): Boolean = {
    left.forall { case (key, value) => right.get(key).contains(value) }
  }

  // when comparing the set of properties for compatibility we want to exclude the qvalue.
  // qvalue is only relevant to resolving preference
  private[this] def stripPropertiesIgnoredDuringComparison(m: Map[String, String]): Map[String, String] = {
    m - "q"
  }
}
