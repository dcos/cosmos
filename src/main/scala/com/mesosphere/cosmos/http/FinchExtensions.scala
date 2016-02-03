package com.mesosphere.cosmos.http

import io.finch.{DecodeRequest, ValidationRule}

object FinchExtensions {

  def beTheExpectedType(expected: MediaType): ValidationRule[MediaType] =
    ValidationRule(s"be of type ${expected.show}") { actual =>
      MediaTypeOps.compatible(expected, actual)
    }

  implicit val decodeMediaType: DecodeRequest[MediaType] = DecodeRequest.instance(s => MediaType.parse(s))

}
