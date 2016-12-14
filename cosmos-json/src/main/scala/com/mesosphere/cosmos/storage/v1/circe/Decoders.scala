package com.mesosphere.cosmos.storage.v1.circe

import cats.data.Xor
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.storage.v1.model.FailedStatus
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.Operation
import com.mesosphere.cosmos.storage.v1.model.OperationFailure
import com.mesosphere.cosmos.storage.v1.model.OperationStatus
import com.mesosphere.cosmos.storage.v1.model.PendingStatus
import com.mesosphere.cosmos.storage.v1.model.PendingOperation
import com.mesosphere.cosmos.storage.v1.model.Uninstall
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe.v3.circe.Decoders._
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.HCursor
import io.circe.generic.semiauto._

object Decoders {

  implicit val decodeInstall: Decoder[Install] =
    deriveDecoder[Install]

  implicit val decodeUniverseInstall: Decoder[UniverseInstall] =
    deriveDecoder[UniverseInstall]

  implicit val decodeUninstall: Decoder[Uninstall] =
    deriveDecoder[Uninstall]

  implicit val decodeOperation: Decoder[Operation] = {
    val InstallName: String = classOf[Install].getSimpleName
    val UninstallName: String = classOf[Uninstall].getSimpleName
    val UniverseInstallName: String = classOf[UniverseInstall].getSimpleName

    Decoder.instance { (hc: HCursor) =>
      hc.downField("type").as[String].flatMap {
        case InstallName => hc.as[Install]
        case UniverseInstallName => hc.as[UniverseInstall]
        case UninstallName => hc.as[Uninstall]
        case tp: String => Xor.left(DecodingFailure(
          s"Encountered unknown type [$tp]" +
            " while trying to decode Operation", hc.history))
      }
    }
  }

  implicit val decodeOperationFailure: Decoder[OperationFailure] =
    deriveDecoder[OperationFailure]

  implicit val decodePendingOperation: Decoder[PendingOperation] =
    deriveDecoder[PendingOperation]

  implicit val decodePending: Decoder[PendingStatus] =
    deriveDecoder[PendingStatus]

  implicit val decodeFailed: Decoder[FailedStatus] =
    deriveDecoder[FailedStatus]

  implicit val decodeOperationStatus: Decoder[OperationStatus] = {
    val PendingName = classOf[PendingStatus].getSimpleName
    val FailedName = classOf[FailedStatus].getSimpleName

    Decoder.instance { (hc: HCursor) =>
      hc.downField("type").as[String].flatMap {
        case PendingName => hc.as[PendingStatus]
        case FailedName => hc.as[FailedStatus]
        case tp: String => Xor.left(DecodingFailure(
          s"Encountered unknown type [$tp]" +
            " while trying to decode OperationStatus", hc.history)
        )
      }
    }
  }

}
