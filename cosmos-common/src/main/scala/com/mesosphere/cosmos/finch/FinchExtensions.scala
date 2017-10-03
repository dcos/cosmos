package com.mesosphere.cosmos.finch

import com.mesosphere.http.CompoundMediaType
import com.mesosphere.http.CompoundMediaTypeParser
import com.mesosphere.http.MediaType
import com.twitter.util.Try
import io.circe.Json
import io.finch.DecodeEntity
import io.finch.Endpoint
import io.finch.ValidationRule
import shapeless.HNil

object FinchExtensions {

  def beTheExpectedTypes(expectedTypes: List[MediaType]): ValidationRule[MediaType] =
    ValidationRule(s"match one of ${expectedTypes.map(_.show).mkString(", ")}") { actual =>
      expectedTypes.exists(expected => MediaType.compatible(expected, actual))
    }

  implicit val decodeMediaType: DecodeEntity[MediaType] = {
    DecodeEntity.instance(s => Try.fromScala(MediaType.parse(s)))
  }

  implicit val decodeCompoundMediaType: DecodeEntity[CompoundMediaType] = {
    DecodeEntity.instance(s => Try.fromScala(CompoundMediaTypeParser.parse(s)))
  }

  def route[Req, Res](
    base: Endpoint[HNil],
    handler: EndpointHandler[Req, Res]
  )(
    requestValidator: Endpoint[EndpointContext[Req, Res]]
  ): Endpoint[Json] = {
    (base :: requestValidator).mapOutputAsync(handler(_))
  }

}
