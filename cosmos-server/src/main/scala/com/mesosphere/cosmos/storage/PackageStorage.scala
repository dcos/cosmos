package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.PackageFileMissing
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.circe.Encoders.exceptionErrorResponse
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.storage.ObjectStorageOps.objectStorageOps
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps.packageDefinitionToPackageDefinitionOps
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import scala.util.Try

final class PackageStorage private(objectStorage: ObjectStorage) {

  import PackageStorage._

  def write(
    packageDefinition: universe.v3.model.V3Package
  ): Future[Unit] = {
    val metadataName = getMetadataName(packageDefinition.packageCoordinate)
    val data = packageDefinition.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    val mediaType = universe.MediaTypes.universeV3Package
    objectStorage.write(metadataName, data, mediaType)
  }

  def read(
    packageCoordinate: rpc.v1.model.PackageCoordinate
  ): Future[Option[universe.v3.model.V3Package]] = {
    val metadataName = getMetadataName(packageCoordinate)
    objectStorage.readAsArray(metadataName).map( _.map { case (_, data) =>
      decode[universe.v3.model.V3Package](new String(data))
    })
  }

  def list(): Future[List[PackageCoordinate]] = {
    objectStorage.listWithoutPaging("").map { objectList =>
      objectList.directories.flatMap(_.as[Try[PackageCoordinate]].toOption)
    }
  }

  def readAllLocalPackages(): Future[List[rpc.v1.model.LocalPackage]] = {
    list().flatMap { coordinates =>
      val localPackages = coordinates.map { coordinate =>
        read(coordinate).map {
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

  def readAllInstalledPackages(): Future[List[universe.v3.model.V3Package]] = {
    readAllLocalPackages().map(_.flatMap {
      case rpc.v1.model.Installed(v3Package) => Some(v3Package)
      case _ => None
    })
  }

}

object PackageStorage {
  def apply(objectStorage: ObjectStorage): PackageStorage = {
    new PackageStorage(objectStorage)
  }

  private def getMetadataName(packageCoordinate: PackageCoordinate): String = {
    s"${packageCoordinate.as[String]}/metadata.json"
  }
}
