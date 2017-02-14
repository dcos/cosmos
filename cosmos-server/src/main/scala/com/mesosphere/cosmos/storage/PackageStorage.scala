package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.PackageFileMissing
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.circe.Encoders.exceptionErrorResponse
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.util.AbsolutePath
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.FuturePool
import io.circe.syntax._
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import scala.util.Try

final class PackageStorage private(objectStorage: ObjectStorage) {

  import PackageStorage._

  private[this] val pool = FuturePool.interruptibleUnboundedPool

  def writePackageDefinition(
    packageDefinition: universe.v3.model.V3Package
  ): Future[Unit] = {
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      packageDefinition.name,
      packageDefinition.version
    )

    val data = packageDefinition.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)

    objectStorage.write(
      AbsolutePath(s"/${packageCoordinate.as[String]}/$MetadataJson").right.get,
      new ByteArrayInputStream(data),
      data.length.toLong,
      universe.MediaTypes.universeV3Package
    )
  }

  def readPackageDefinition(
    packageCoordinate: rpc.v1.model.PackageCoordinate
  ): Future[Option[universe.v3.model.V3Package]] = {
    val path = AbsolutePath(s"/${packageCoordinate.as[String]}/$MetadataJson").right.get

    objectStorage.read(path).flatMap {
      case Some((_, inputStream)) =>
        pool {
          Some(
            decode[universe.v3.model.V3Package](
              new String(
                StreamIO.buffer(inputStream).toByteArray,
                StandardCharsets.UTF_8
              )
            )
          )
        }.ensure(inputStream.close())
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
            val newPackageCoordinates = getPackageCoordinates(objectList.directories)

            listing(
              newPackageCoordinates ++ packageCoordinates,
              objectList.listToken
            )
          }

        case None =>
          Future.value(packageCoordinates)
      }
    }

    objectStorage.list(AbsolutePath.Root).flatMap { objectList =>
      /* TODO: This ignores errors for now. Need to revisit and figure out what to
       * do with parsing errors.
       */
      listing(getPackageCoordinates(objectList.directories), objectList.listToken)
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
                exceptionErrorResponse(PackageFileMissing(MetadataJson)),
                packageCoordinate
              )
          }
        }
      } map(_.toList)
    }
  }
}

object PackageStorage {

  val MetadataJson = "metadata.json"

  def apply(objectStorage: ObjectStorage): PackageStorage = {
    new PackageStorage(objectStorage)
  }

  def getPackageCoordinates(directoryPaths: List[AbsolutePath]): List[PackageCoordinate] = {
    for {
      directoryPath <- directoryPaths
      lastElement <- directoryPath.elements.lastOption
      coordinate <- lastElement.as[Try[rpc.v1.model.PackageCoordinate]].toOption
    } yield coordinate
  }

}
