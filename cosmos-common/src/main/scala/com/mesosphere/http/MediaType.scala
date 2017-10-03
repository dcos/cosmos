package com.mesosphere.http

import io.circe.Encoder
import scala.util.Try

case class MediaTypeSubType(value: String, suffix: Option[String] = None)

object MediaTypeSubType {
  def parse(s: String): MediaTypeSubType = {
    s.split('+').toList match {
      case v :: suffix :: Nil => new MediaTypeSubType(v.toLowerCase, Some(suffix.toLowerCase))
      case v :: Nil => new MediaTypeSubType(v.toLowerCase, None)
      case _ => throw new IllegalStateException(s"Unable to parse suffix from sub-type for value: '$s'")
    }
  }
}

/**
  * https://www.w3.org/Protocols/rfc1341/4_Content-Type.html
  *
  */
case class MediaType(
  `type`: String,
  subType: MediaTypeSubType,
  parameters: Map[String, String] = Map.empty
) {

  val show: String = {
    val t = subType match {
      case MediaTypeSubType(st, Some(suf)) =>
        s"${`type`}/$st+$suf"
      case MediaTypeSubType(st, None) =>
        s"${`type`}/$st"
    }
    val p = parameters.toVector
      .map { case (key, value) => s";$key=$value" }
      .mkString

    t + p
  }
}


object MediaType {

  implicit val encodeMediaType: Encoder[MediaType] = Encoder.encodeString.contramap(_.show)

  def unapply(s: String): Option[MediaType] = {
    parse(s).toOption
  }

  def apply(t: String, st: String): MediaType = {
    // TODO package-add: Enforce validation with a single code path for instantiating MediaType
    assert(t.nonEmpty, "`type` must not be empty")
    assert(st.nonEmpty, "`subType` must not be empty")
    MediaType(t, MediaTypeSubType(st))
  }

  def parse(s: String): Try[MediaType] = {
    MediaTypeParser.parse(s)
  }

  def vndJson(namespace: List[String])(kind: String, version: Int): MediaType = {
    assert(namespace.nonEmpty, "`namespace` must not be empty")
    assert(kind.trim.nonEmpty, "`kind` must not be empty")
    assert(version > 0, "`version` must be > 0")
    MediaType(
      "application",
      MediaTypeSubType(s"vnd.${namespace.mkString(".")}.$kind", Some("json")),
      Map("charset" -> "utf-8", "version" -> ("v" + version))
    )
  }

  implicit final class MediaTypeOps(val mediaType: MediaType) extends AnyVal {
    def isCompatibleWith(other: MediaType): Boolean = {
      compatible(mediaType, other)
    }

    /**
     * Behavior note: right now this function does not differentiate between
     *  * `mediaType` does not have a q parameter defined and
     *  * `mediaType` does have a q parameter defined but parsing failed
     */
    def qValue: QualityValue = {
      MediaType.qValue(mediaType)
    }
  }

  def compatible(expected: MediaType, actual: MediaType): Boolean = {
    compatibleIgnoringParameters(expected, actual) &&
      isLeftSubSetOfRight(
        stripPropertiesIgnoredDuringComparison(expected.parameters),
        stripPropertiesIgnoredDuringComparison(actual.parameters)
      )
  }

  def compatibleIgnoringParameters(
    expected: MediaType,
    actual: MediaType
  ): Boolean = {
    expected.`type` == actual.`type` && expected.subType == actual.subType
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
        /* the higher the number of parameters the higher priority it should
         * be, flip the sign here to get that cheaply
         */
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
