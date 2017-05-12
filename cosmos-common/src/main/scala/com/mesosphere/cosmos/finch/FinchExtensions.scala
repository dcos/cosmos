package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.CompoundMediaType
import com.mesosphere.cosmos.http.CompoundMediaTypeParser
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeOps
import io.circe.Json
import io.finch.DecodeEntity
import io.finch.Endpoint
import io.finch.ValidationRule
import shapeless.HNil

object FinchExtensions {

  def beTheExpectedTypes(expectedTypes: List[MediaType]): ValidationRule[MediaType] =
    ValidationRule(s"match on of ${expectedTypes.map(_.show).mkString(", ")}") { actual =>
      expectedTypes.exists(expected => MediaTypeOps.compatible(expected, actual))
    }

  implicit val decodeMediaType: DecodeEntity[MediaType] = {
    DecodeEntity.instance(s => MediaType.parse(s))
  }

  implicit val decodeCompoundMediaType: DecodeEntity[CompoundMediaType] = {
    DecodeEntity.instance(s => CompoundMediaTypeParser.parse(s))
  }

  def route[Req, Res](
    base: Endpoint[HNil],
    handler: EndpointHandler[Req, Res]
  )(
    requestValidator: Endpoint[EndpointContext[Req, Res]]
  ): Endpoint[Json] = {
    (base :: requestValidator).apply((context: EndpointContext[Req, Res]) => handler(context))
  }

}
