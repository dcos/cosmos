package com.mesosphere.cosmos.storage.v1.model

import cats.syntax.either._
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._

import scala.util.Left

sealed trait UninstallStatus
case object InProgress extends UninstallStatus
case object Failed extends UninstallStatus

object UninstallStatus {
  val UninstallStatusMediaType = MediaType.vndJson(List("dcos", "package"))("uninstall.status", 1)

  implicit val decodeFailed: Decoder[Failed.type] =
    deriveDecoder[Failed.type]
  implicit val decodeInProgress: Decoder[InProgress.type] =
    deriveDecoder[InProgress.type]

  implicit val encodeFailed: Encoder[Failed.type] =
    deriveEncoder[Failed.type]
  implicit val encodeInProgress: Encoder[InProgress.type] =
    deriveEncoder[InProgress.type]

  implicit val decodeUninstallStatus: Decoder[UninstallStatus] = {
    val InProgressName = InProgress.getClass.getSimpleName
    val FailedName = Failed.getClass.getSimpleName

    Decoder.instance { (hc: HCursor) =>
      hc.downField("type").as[String].flatMap {
        case InProgressName => hc.as[InProgress.type]
        case FailedName => hc.as[Failed.type]
        case tp: String => Left(DecodingFailure(
          s"Encountered unknown type [$tp]" +
           " while trying to decode UninstallStatus", hc.history
        ))
      }
    }
  }

  implicit val encodeUninstallStatus: Encoder[UninstallStatus] = {
    Encoder.instance { uninstallStatus =>
      val (json: Json, subclass: String) = uninstallStatus match {
        case inProgress: InProgress.type =>
          (inProgress.asJson, inProgress.getClass.getSimpleName)
        case failed: Failed.type =>
          (failed.asJson, failed.getClass.getSimpleName)
      }
      json.mapObject(_.add("type", subclass.asJson))
    }
  }

  implicit val uninstallStatusMediaDecoder: MediaTypedDecoder[UninstallStatus] =
    MediaTypedDecoder[UninstallStatus](UninstallStatusMediaType)

  implicit val uninstallStatusEncoder: MediaTypedEncoder[UninstallStatus] =
    MediaTypedEncoder[UninstallStatus](UninstallStatusMediaType)
}
