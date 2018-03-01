package com.mesosphere.cosmos.circe

import cats.data.Ior
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe.common.ByteBuffers
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import com.twitter.util.Duration
import com.twitter.util.StorageUnit
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.CursorOp.opsToPath
import io.circe.JsonObject
import io.circe.ParsingFailure
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.finch.Error
import io.finch.Errors
import io.netty.handler.codec.http.HttpResponseStatus
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Base64
import org.jboss.netty.handler.codec.http.HttpMethod

object Encoders {

  implicit val exceptionEncoder: Encoder[Exception] = {
    Encoder.instance { e =>
      exceptionErrorResponse(e).asJson
    }
  }

  implicit val encodeUri: Encoder[Uri] = Encoder.instance(_.toString.asJson)

  implicit val encodeStorageUnit: Encoder[StorageUnit] = Encoder.instance {
    _.bytes.toString.asJson
  }

  implicit val encodeByteBuffer: Encoder[ByteBuffer] = Encoder.instance { bb =>
    Base64.getEncoder.encodeToString(ByteBuffers.getBytes(bb)).asJson
  }

  implicit val encodeDurationToSeconds: Encoder[Duration] = Encoder.instance {
    duration =>
      s"${duration.inSeconds} seconds".asJson
  }
  
  implicit val encodePath: Encoder[Path] =
    Encoder.encodeString.contramap(_.toString)

  implicit val encodeStatus: Encoder[Status] =
    Encoder.encodeInt.contramap(_.code)

  implicit val encodeNettyStatus: Encoder[HttpResponseStatus] =
    Encoder.encodeInt.contramap(_.code)

  implicit val encodeHttpMethod: Encoder[HttpMethod] =
    Encoder.encodeString.contramap(_.getName)

  implicit def encodeIor[A, B](
    implicit encodeA: Encoder[A],
    encodeB: Encoder[B]
  ): Encoder[Ior[A, B]] = deriveEncoder[Ior[A, B]]

  def exceptionErrorResponse(t: Throwable): rpc.v1.model.ErrorResponse =
    t match {
      case circeError: io.circe.Error => circeErrorResponse(circeError)
      case Error.NotPresent(item) =>
        rpc.v1.model.ErrorResponse(
          "not_present",
          s"Item ${item.description} not present but required"
        )
      case Error.NotParsed(item, _, cause) =>
        rpc.v1.model.ErrorResponse(
          "not_parsed",
          s"Item '${item.description}' unable to be parsed: ${cause.getMessage}"
        )
      case Error.NotValid(item, rule) =>
        rpc.v1.model.ErrorResponse(
          "not_valid",
          s"Item ${item.description} deemed invalid by rule: $rule"
        )
      case Errors(ts) =>
        val details = ts.map(exceptionErrorResponse).toList.asJson
        rpc.v1.model.ErrorResponse(
          "multiple_errors",
          "Multiple errors while processing request",
          Some(JsonObject.singleton("errors", details))
        )
      case ce: CosmosException => rpc.v1.model.ErrorResponse(ce.error)
      case t: Throwable =>
        rpc.v1.model.ErrorResponse("unhandled_exception", Option(t.getMessage).getOrElse(""))
    }

  private[this] def circeErrorResponse(
      circeError: io.circe.Error
  ): rpc.v1.model.ErrorResponse = {
    circeError match {
      case pf: ParsingFailure =>
        rpc.v1.model.ErrorResponse(
          "json_error",
          s"Json parsing failure '${pf.message}'",
          data = Some(
            JsonObject.fromMap(
              Map(
                "type" -> "parse".asJson,
                "reason" -> pf.message.asJson
              )
            )
          )
        )
      case df: DecodingFailure =>
        val path = opsToPath(df.history)
        rpc.v1.model.ErrorResponse(
          "json_error",
          s"Json decoding failure '${df.message}' at: $path",
          data = Some(
            JsonObject.fromMap(
              Map(
                "type" -> "decode".asJson,
                "reason" -> df.message.asJson,
                "path" -> path.asJson
              )
            )
          )
        )
    }
  }
}
