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
    expected.`type` == actual.`type` && expected.subType == actual.subType
  }

  def compatible(expected: MediaType, actual: MediaType): Boolean = {
    compatibleIgnoringParameters(expected, actual) &&
      isLeftSubSetOfRight(expected.parameters, actual.parameters)
  }

  private[this] def isLeftSubSetOfRight(left: Map[String, String], right: Map[String, String]): Boolean = {
    left.forall { case (key, value) => right.get(key).contains(value) }
  }
}
