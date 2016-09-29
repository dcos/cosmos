package com.mesosphere.cosmos.circe

import com.netaporter.uri.Uri

import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.storage.PackageCoordinate
import io.circe.Decoder
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import com.mesosphere.cosmos.storage.{ Install, Operation, PackageCoordinate, PackageQueueContents, Uninstall }

import io.circe.{ Decoder, HCursor }
import io.circe.generic.semiauto._
 import io.circe.jawn.decode

import scala.util.Either

object Decoders {

  implicit val decodeErrorResponse: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]

  implicit val decodeZooKeeperStorageEnvelope: Decoder[ZooKeeperStorageEnvelope] =
    deriveDecoder[ZooKeeperStorageEnvelope]

  implicit val decodePackageCoordinate: Decoder[PackageCoordinate] =
    deriveDecoder[PackageCoordinate]

  implicit val decodeOperationInstall: Decoder[Operation] =
    Decoder.decodeString.map {
      case "install" => Install
      case "uninstall" => Uninstall
    }

  implicit val decodeEitherPackageQueue: Decoder[Either[PackageDefinition, Uri]] =
    Decoder[PackageDefinition].map(Left(_)).or(Decoder[Uri].map(Right(_)))

  implicit val decodePackageQueueContents: Decoder[PackageQueueContents] =
    deriveDecoder[PackageQueueContents]

}
