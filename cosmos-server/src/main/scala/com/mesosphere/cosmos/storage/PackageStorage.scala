package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.PackageFileMissing
import com.mesosphere.cosmos.UnknownMediaType
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.circe.Encoders.exceptionErrorResponse
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps.packageDefinitionToPackageDefinitionOps
import com.mesosphere.util.AbsolutePath
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import scala.util.Try

final class PackageStorage private(objectStorage: ObjectStorage) {

  import PackageStorage._

  def writePackageDefinition(
    packageDefinition: universe.v3.model.SupportedPackageDefinition
  ): Future[Unit] = {
    val metadataName = getMetadataPath(packageDefinition.packageCoordinate)
    val data = packageDefinition.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    val mediaType = packageDefinition match {
      case _: universe.v3.model.V3Package => universe.MediaTypes.universeV3Package
    }
    objectStorage.write(metadataName, data, mediaType)
  }

  def readPackageDefinition(
    packageCoordinate: rpc.v1.model.PackageCoordinate
  ): Future[Option[universe.v3.model.SupportedPackageDefinition]] = {
    val metadataName = getMetadataPath(packageCoordinate)
    objectStorage.readAsArray(metadataName).map { pendingRead =>
      pendingRead.map {
        case (universe.MediaTypes.universeV3Package, data) =>
          decode[universe.v3.model.SupportedPackageDefinition](
            new String(data, StandardCharsets.UTF_8)
          )
        case (mt, _) =>
          throw UnknownMediaType(mt.show)
      }
    }
  }

  private[this] def list(): Future[List[PackageCoordinate]] = {
    objectStorage.listWithoutPaging(AbsolutePath.Root)
      .map(objectList => getPackageCoordinates(objectList.directories))
  }

  def readAllLocalPackages(): Future[List[rpc.v1.model.LocalPackage]] = {
    list().flatMap { coordinates =>
      val localPackages = coordinates.map { coordinate =>
        readPackageDefinition(coordinate).map {
          case Some(packageDefinition) =>
            rpc.v1.model.Installed(packageDefinition)
          case None =>
            rpc.v1.model.Invalid(
              exceptionErrorResponse(PackageFileMissing("metadata.json")),
              coordinate
            )
        }
      }
      Future.collect(localPackages).map(_.toList)
    }
  }

}

object PackageStorage {

  val MetadataJson = "metadata.json"

  def apply(objectStorage: ObjectStorage): PackageStorage = {
    new PackageStorage(objectStorage)
  }

  private def getMetadataPath(packageCoordinate: PackageCoordinate): AbsolutePath = {
    AbsolutePath.Root / packageCoordinate.as[String] / "metadata.json"
  }

  def getPackageCoordinates(directoryPaths: List[AbsolutePath]): List[PackageCoordinate] = {
    for {
      directoryPath <- directoryPaths
      lastElement <- directoryPath.elements.lastOption
      coordinate <- lastElement.as[Try[rpc.v1.model.PackageCoordinate]].toOption
    } yield coordinate
  }

}
