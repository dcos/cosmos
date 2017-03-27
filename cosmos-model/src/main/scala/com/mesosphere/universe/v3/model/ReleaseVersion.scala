package com.mesosphere.universe.v3.model

import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try

final class ReleaseVersion private(val value: Long) extends AnyVal

object ReleaseVersion {

  def apply(value: Long): Try[ReleaseVersion] = { //TODO: do not return Try. Most call sites do .get
    if (value >= 0) {
      Return(new ReleaseVersion(value))
    } else {
      val message = s"Expected integer value >= 0 for release version, but found [$value]"
      Throw(new IllegalArgumentException(message))
    }
  }

  implicit val packageDefinitionReleaseVersionOrdering: Ordering[ReleaseVersion] = {
    Ordering.by(_.value)
  }

}
