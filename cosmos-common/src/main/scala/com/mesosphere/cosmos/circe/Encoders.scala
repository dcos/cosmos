package com.mesosphere.cosmos.circe

import cats.data.Ior
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.twitter.finagle.http.Status
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HistoryOp.opsToPath
import io.circe.JsonObject
import io.circe.ParsingFailure
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.finch.Error
import io.finch.Errors
import org.jboss.netty.handler.codec.http.HttpMethod

object Encoders {

  /*
  implicit val exceptionEncoder: Encoder[Exception] = {
    Encoder.instance { e => exceptionErrorResponse(e).asJson }
  }*/

  implicit val encodeStatus: Encoder[Status] = Encoder.encodeInt.contramap(_.code)
  implicit val encodeMediaType: Encoder[MediaType] = Encoder.encodeString.contramap(_.show)
  implicit val encodeHttpMethod: Encoder[HttpMethod] = Encoder.encodeString.contramap(_.getName)

  implicit def encodeIor[A, B](implicit
    encodeA: Encoder[A],
    encodeB: Encoder[B]
  ): Encoder[Ior[A, B]] = deriveEncoder[Ior[A, B]]

  def exceptionErrorResponse(t: Throwable): ErrorResponse = t match {
    case circeError: io.circe.Error => circeErrorResponse(circeError)
    case Error.NotPresent(item) =>
      ErrorResponse("not_present", s"Item ${item.description} not present but required")
    case Error.NotParsed(item, _, cause) =>
      ErrorResponse(
        "not_parsed",
        s"Item '${item.description}' unable to be parsed: ${cause.getMessage}"
      )
    case Error.NotValid(item, rule) =>
      ErrorResponse("not_valid", s"Item ${item.description} deemed invalid by rule: $rule")
    case Errors(ts) =>
      val details = ts.map(exceptionErrorResponse).toList.asJson
      ErrorResponse(
        "multiple_errors",
        "Multiple errors while processing request",
        Some(JsonObject.singleton("errors", details))
      )
    case ce: CosmosException => ce.errorResponse
    case t: Throwable =>
      ErrorResponse("unhandled_exception", t.getMessage)
  }

  private[this] def circeErrorResponse(circeError: io.circe.Error): ErrorResponse = circeError match {
    case pf: ParsingFailure =>
      ErrorResponse(
        "json_error",
        s"Json parsing failure '${pf.message}'",
        data = Some(JsonObject.fromMap(Map(
          "type" -> "parse".asJson,
          "reason" -> pf.message.asJson
        )))
      )
    case df: DecodingFailure =>
      val path = opsToPath(df.history)
      ErrorResponse(
        "json_error",
        s"Json decoding failure '${df.message}' at: $path",
        data = Some(JsonObject.fromMap(Map(
          "type" -> "decode".asJson,
          "reason" -> df.message.asJson,
          "path" -> path.asJson
        )))
      )
  }
}
