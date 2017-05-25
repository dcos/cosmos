package com.mesosphere.cosmos.circe

import cats.data.Ior
import com.mesosphere.cosmos.CosmosException
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.LocalPackage
import com.mesosphere.cosmos.storage.v1.model.FailedStatus
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.Operation
import com.mesosphere.cosmos.storage.v1.model.OperationFailure
import com.mesosphere.cosmos.storage.v1.model.OperationStatus
import com.mesosphere.cosmos.storage.v1.model.PendingOperation
import com.mesosphere.cosmos.storage.v1.model.PendingStatus
import com.mesosphere.cosmos.storage.v1.model.Uninstall
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe
import com.mesosphere.util.PackageUtil
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HistoryOp.opsToPath
import io.circe.Json
import io.circe.JsonObject
import io.circe.ParsingFailure
import io.circe.generic.encoding.DerivedObjectEncoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.finch.Error
import io.finch.Errors
import org.jboss.netty.handler.codec.http.HttpMethod


object Encoders {

  implicit val encodeInstall: Encoder[Install] =
    deriveEncoder[Install]

  implicit val encodeUniverseInstall: Encoder[UniverseInstall] =
    deriveEncoder[UniverseInstall]

  implicit val encodeUninstall: Encoder[Uninstall] =
    deriveEncoder[Uninstall]

  implicit val encodeOperation: Encoder[Operation] =
    Encoder.instance { operation =>
      val (json: Json, subclass: String) = operation match {
        case i: Install => (i.asJson, i.getClass.getSimpleName)
        case ui: UniverseInstall => (ui.asJson, ui.getClass.getSimpleName)
        case u: Uninstall => (u.asJson, u.getClass.getSimpleName)
      }

      json.mapObject(_.add("type", subclass.asJson))
    }

  implicit val encodeOperationFailure: Encoder[OperationFailure] =
    deriveEncoder[OperationFailure]

  implicit val encodePendingOperation: Encoder[PendingOperation] =
    deriveEncoder[PendingOperation]

  implicit val encodePending: Encoder[PendingStatus] =
    deriveEncoder[PendingStatus]

  implicit val encodeFailedStatus: Encoder[FailedStatus] =
    deriveEncoder[FailedStatus]

  implicit val encodeOperationStatus: Encoder[OperationStatus] = {
    Encoder.instance { operationStatus =>
      val (json: Json, subclass: String) = operationStatus match {
        case pending: PendingStatus =>
          (pending.asJson, pending.getClass.getSimpleName)
        case failed: FailedStatus =>
          (failed.asJson, failed.getClass.getSimpleName)
      }
      json.mapObject(_.add("type", subclass.asJson))
    }
  }

  /* This encoder converts a LocalPackage into a JSON object. The total number of fields are
   * enumerated below.
   *
   * {
   *   "status": <String>,
   *   "metadata": <PackageDefinition>,
   *   "operation": <Operation>,
   *   "error": <ErrorResponse>,
   *   "packageCoordinate": <PackageCoordinate>
   * }
   *
   * The 'status' will always be set while the rest of the properties are optional.
   */
  implicit val encodeLocalPackage = new Encoder[LocalPackage] {
    final override def apply(value: LocalPackage): Json = {
      val dataField = value.metadata.fold(
        pc => ("packageCoordinate", pc.asJson),
        pkg => ("metadata", pkg.asJson)
      )

      Json.obj(
        "status" -> Json.fromString(value.getClass.getSimpleName),
        dataField,
        "error" -> value.error.asJson,
        "operation" -> value.operation.asJson
      )
    }
  }

  implicit val exceptionEncoder: Encoder[Exception] = {
    Encoder.instance { e => exceptionErrorResponse(e).asJson }
  }

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
