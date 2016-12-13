package com.mesosphere.cosmos.circe

import cats.data.Xor
import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.Install
import com.mesosphere.cosmos.rpc.v1.model.Operation
import com.mesosphere.cosmos.rpc.v1.model.Uninstall
import com.mesosphere.cosmos.rpc.v1.model.UniverseInstall
import com.mesosphere.cosmos.storage.installqueue.OperationFailure
import com.mesosphere.cosmos.storage.installqueue.PendingOperation
import com.mesosphere.cosmos.storage.installqueue._
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v3.circe.Decoders._
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.HCursor
import io.circe.generic.semiauto._

object Decoders {
  def decode[T](value: String)(implicit decoder: Decoder[T]): T = {
    io.circe.jawn.decode[T](value) match {
      case Xor.Right(result) => result
      case Xor.Left(error) => throw CirceError(error)
    }
  }

  implicit val decodeZooKeeperStorageEnvelope: Decoder[ZooKeeperStorageEnvelope] =
    deriveDecoder[ZooKeeperStorageEnvelope]

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

  implicit val decodePending: Decoder[Pending] =
    deriveDecoder[Pending]

  implicit val decodeFailed: Decoder[Failed] =
    deriveDecoder[Failed]

  implicit val decodeOperationStatus: Decoder[OperationStatus] = {
    val PendingName = classOf[Pending].getSimpleName
    val FailedName = classOf[Failed].getSimpleName

    Decoder.instance { (hc: HCursor) =>
      hc.downField("type").as[String].flatMap {
        case PendingName => hc.as[Pending]
        case FailedName => hc.as[Failed]
        case tp: String => Xor.left(DecodingFailure(
          s"Encountered unknown type [$tp]" +
            " while trying to decode OperationStatus", hc.history)
        )
      }
    }
  }

}
