package com.mesosphere.universe.v3.model

import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import java.util.regex.Pattern

final class Tag private(val value: String) extends AnyVal {

  override def toString: String = value

}

object Tag {

  val packageDetailsTagRegex: String = "^[^\\s]+$"
  val packageDetailsTagPattern: Pattern = Pattern.compile(packageDetailsTagRegex)

  def apply(s: String): Try[Tag] = {
    if (packageDetailsTagPattern.matcher(s).matches()) {
      Return(new Tag(s))
    } else {
      Throw(new IllegalArgumentException(
        s"Value '$s' does not conform to expected format $packageDetailsTagRegex"
      ))
    }
  }

}
