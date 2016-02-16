package com.mesosphere.cosmos.http

import io.finch.{DecodeRequest, ValidationRule}

object FinchExtensions {

  def beTheExpectedType(expected: MediaType): ValidationRule[MediaType] =
    ValidationRule(s"match ${expected.show}") { actual =>
      MediaTypeOps.compatible(expected, actual)
    }

  def beOneOfTheExpectedTypes(acceptable: Set[MediaType]): ValidationRule[MediaType] =
    ValidationRule(s"match one of ${acceptable.map(_.show).mkString(",")}") { actual =>
      acceptable.exists(MediaTypeOps.compatible(_, actual))
    }

  implicit val decodeMediaType: DecodeRequest[MediaType] = DecodeRequest.instance(s => MediaType.parse(s))

}
