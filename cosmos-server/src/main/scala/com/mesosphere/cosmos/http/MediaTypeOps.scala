package com.mesosphere.cosmos.http

class MediaTypeOps(val mediaType: MediaType) extends AnyVal {
  def isCompatibleWith(other: MediaType): Boolean = {
    MediaTypeOps.compatible(mediaType, other)
  }
}

object MediaTypeOps {
  import scala.language.implicitConversions

  implicit def mediaTypeToMediaTypeOps(mt: MediaType): MediaTypeOps = {
    new MediaTypeOps(mt)
  }

  def compatibleIgnoringParameters(expected: MediaType, actual: MediaType): Boolean = {
    (expected.subType, actual.subType) match {
      case (MediaTypeSubType(eT, Some(eS)), MediaTypeSubType(aT, Some(aS))) =>
        eT == aT && eS == aS
      case (MediaTypeSubType(eT, Some(eS)), MediaTypeSubType(aT, None)) =>
        false
      case (MediaTypeSubType(eT, None), MediaTypeSubType(aT, Some(aS))) =>
        false
      case (MediaTypeSubType(eT, None), MediaTypeSubType(aT, None)) =>
        eT == aT
    }
  }

  def compatible(expected: MediaType, actual: MediaType): Boolean = {
    val subTypesCompatible = compatibleIgnoringParameters(expected, actual)

    val paramsCompatible = (expected.parameters, actual.parameters) match {
      case (None, None) => true
      case (None, Some(_)) => true
      case (Some(_), None) => false
      case (Some(l), Some(r)) => isLeftSubSetOfRight(l, r)
    }

    expected.`type` == actual.`type` && subTypesCompatible && paramsCompatible
  }

  private[this] case class C(l: String, r: Option[String])
  private[this] def isLeftSubSetOfRight(left: Map[String, String], right: Map[String, String]): Boolean = {
    left
      .map { case (key, value) =>
        C(value, right.get(key))
      }
      .forall {
        case C(l, Some(r)) => l == r
        case C(l, None) => false
      }
  }
}
