package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.{CompoundMediaType, CompoundMediaTypeParser, MediaType, MediaTypeOps}
import io.circe.Json
import io.finch.{DecodeRequest, Endpoint, ValidationRule}
import shapeless.HNil

object FinchExtensions {

  def beTheExpectedType(expected: MediaType): ValidationRule[MediaType] =
    ValidationRule(s"match ${expected.show}") { actual =>
      MediaTypeOps.compatible(expected, actual)
    }

  implicit val decodeMediaType: DecodeRequest[MediaType] = {
    DecodeRequest.instance(s => MediaType.parse(s))
  }

  implicit val decodeCompoundMediaType: DecodeRequest[CompoundMediaType] = {
    DecodeRequest.instance(s => CompoundMediaTypeParser.parse(s))
  }

  def route[Req, Res](
    base: Endpoint[HNil],
    handler: EndpointHandler[Req, Res]
  )(
    requestReader: Endpoint[EndpointContext[Req, Res]]
  ): Endpoint[Json] = {
    (base ? requestReader).apply((context: EndpointContext[Req, Res]) => handler(context))
  }

}
