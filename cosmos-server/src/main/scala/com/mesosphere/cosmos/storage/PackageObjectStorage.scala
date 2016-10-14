package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v2.circe.Decoders._
import com.mesosphere.cosmos.rpc.v2.circe.Encoders._
import com.mesosphere.universe
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
  // TODO: Change the type from DescribeResponse; it should not contain selected but publish date
  def writePackageDefinition(
    packageDefinition: rpc.v2.model.DescribeResponse
  ): Future[Unit] = {
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      packageDefinition.name,
      packageDefinition.version
    )


    val path = s"${packageCoordinate.as[String]}/metadata.json"

    val data = packageDefinition.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)

    objectStorage.write(
      path,
      new ByteArrayInputStream(data),
      data.length.toLong,
      MediaTypes.applicationJson // TODO: replace this with the actual media type
    )
  }

  def readPackageDefinition(
    packageCoordinate: rpc.v1.model.PackageCoordinate
  ): Future[Option[rpc.v2.model.DescribeResponse]] = {
    val path = s"${packageCoordinate.as[String]}/metadata.json"

    objectStorage.read(path).flatMap {
      case Some((mediaType, reader)) =>
        // TODO: Check the media type but for now assume it is the correct type
        Reader.readAll(reader).map { buffer =>
          // TODO: Make this a helper. We use this pattern in a few places.
          Some(
            decode[rpc.v2.model.DescribeResponse](
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
              // TODO: Return the correct error
              rpc.v1.model.Invalid(
                "Missing metadata.json",
                packageCoordinate
              )
          }
        }
      } map(_.toList)
    }
  }
}
