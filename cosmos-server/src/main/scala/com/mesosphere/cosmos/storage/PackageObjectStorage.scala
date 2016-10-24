package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.PackageFileMissing
import com.mesosphere.cosmos.circe.Encoders.exceptionErrorResponse
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.Buf.ByteArray
import com.twitter.io.Reader
import com.twitter.util.Future
import io.circe.jawn.decode
import io.circe.syntax._
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import scala.util.Try

final class PackageObjectStorage(objectStorage: ObjectStorage) {
  def writePackageDefinition(
    packageDefinition: universe.v3.model.PackageDefinition
  ): Future[Unit] = {
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      packageDefinition.name,
      packageDefinition.version
    )

    val path = s"${packageCoordinate.as[String]}/metadata.json"

    val data = packageDefinition.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)

    val mediaType = packageDefinition match {
      case _: universe.v3.model.V2Package => universe.MediaTypes.universeV2Package
      case _: universe.v3.model.V3Package => universe.MediaTypes.universeV3Package
    }

    objectStorage.write(
      path,
      new ByteArrayInputStream(data),
      data.length.toLong,
      mediaType
    )
  }

  def readPackageDefinition(
    packageCoordinate: rpc.v1.model.PackageCoordinate
  ): Future[Option[universe.v3.model.PackageDefinition]] = {
    val path = s"${packageCoordinate.as[String]}/metadata.json"

    objectStorage.read(path).flatMap {
      case Some((mediaType, reader)) =>
        Reader.readAll(reader).map { buffer =>
          // TODO: Make this a helper. We use this pattern in a few places.
          Some(
            decode[universe.v3.model.PackageDefinition](
              new String(
                ByteArray.Owned.extract(buffer),
                StandardCharsets.UTF_8
              )
            ).valueOr(err => throw new CirceError(err))
          )
        }
      case None =>
        Future.value(None)
    }
  }

  def list(): Future[List[rpc.v1.model.LocalPackage]] = {
    def listing(
      packageCoordinates: List[rpc.v1.model.PackageCoordinate],
      listToken: Option[ObjectStorage.ListToken]
    ): Future[List[rpc.v1.model.PackageCoordinate]] = {
      listToken match {
        case Some(token) =>
          objectStorage.listNext(token).flatMap { objectList =>
            val newPackageCoordinates = objectList.directories.flatMap { directory =>
              directory.as[Try[rpc.v1.model.PackageCoordinate]].toOption
            }

            listing(
              newPackageCoordinates ++ packageCoordinates,
              objectList.listToken
            )
          }

        case None =>
          Future.value(packageCoordinates)
      }
    }

    objectStorage.list("").flatMap { objectList =>
      /* TODO: This ignores errors for now. Need to revisit and figure out what to
       * do with parsing errors.
       */
      listing(
        objectList.directories.flatMap { directory =>
          directory.as[Try[rpc.v1.model.PackageCoordinate]].toOption
        },
        objectList.listToken
      )
    } flatMap { packageCoordinates =>
      Future.collect {
        packageCoordinates.map { packageCoordinate =>
          readPackageDefinition(packageCoordinate).map {
            case Some(packageDefinition) =>
              rpc.v1.model.Installed(
                packageDefinition
              )
            case None =>
              rpc.v1.model.Invalid(
                exceptionErrorResponse(PackageFileMissing("metadata.json")),
                packageCoordinate
              )
          }
        }
      } map(_.toList)
    }
  }
}
