package com.mesosphere.http

import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
  * Case class that represents the QualityValue defined in the HTTP RFC
  * https://tools.ietf.org/html/rfc7231#section-5.3.1
  *
  * @param quality "a real number in the range 0 through 1, where 0.001 is the least preferred and 1 is the most
  *                preferred; a value of 0 means "not acceptable".  If no "q" parameter is present, the default
  *                weight is 1."
  */
case class QualityValue private[http](quality: Double)

object QualityValue {
  val default = QualityValue(1.0)
  val zero = QualityValue(0.0)

  def parse(value: String): Try[QualityValue] = {
    Try { validateString(value) }
      .map { _.toDouble}
      .recoverWith {
        case _: NumberFormatException => Failure(err(value))
      }
      .map(QualityValue.apply)
  }

  def getFromMediaType(mt: MediaType): Try[Option[QualityValue]] = {
    mt.parameters.get("q") match {
      case None => Success(None)
      case Some(s) => parse(s).map(Some(_))
    }
  }

  implicit val qualityValueOrdering: Ordering[QualityValue] = Ordering.by(_.quality)

  /**
    * According to the HTTP/1.1 Spec when a media type specifies a quality value it must be in the range 0 to 1.0
    * (inclusive) and is allowed to specify up to a maximum of 3 decimal places. This method does a range check to
    * ensure that the provide value `s` falls into this range before we convert the value to a double.  This works
    * by exploiting the fact that the `<=` operator used for comparison is actually syntactic sugar for
    * scala.math.Ordering.String which ultimately delegates to java.lang.String#compareTo(java.lang.String).
    *
    * See the tests for some specific examples being evaluated.
    *
    * @see https://tools.ietf.org/html/rfc7231#section-5.3.1
    */
  private[this] def validateString(s: String): String = {
    if (
      (s.charAt(0) == '0' || s.charAt(0) == '1')
        && s.length <= 5
        && "0.0" <= s && s <= "1.000"
    ) {
      s
    } else {
      throw err(s)
    }
  }

  private[this] def err(value: String): IllegalArgumentException = {
    new IllegalArgumentException(
      s"Unexpected q value '$value'. Expected 0.0 <= q <= 1.0 with no more than 3 decimal places"
    )
  }
}
