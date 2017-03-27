package com.mesosphere.cosmos.handler

import cats.syntax.either._
import com.mesosphere.cosmos.InvalidPackage
import com.mesosphere.cosmos.InvalidPackageVersionForAdd
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.ProducerView
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.model.Metadata
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.util.PackageUtil
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import com.twitter.util.FuturePool
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.util.UUID

final class PackageAddHandler(
  packageCollection: PackageCollection,
  stagedPackageStorage: StagedPackageStorage,
  producerView: ProducerView
) extends EndpointHandler[rpc.v1.model.AddRequest, rpc.v1.model.AddResponse](
  successStatus = Status.Accepted
) {

  private[this] val pool = FuturePool.interruptibleUnboundedPool

  override def apply(request: rpc.v1.model.AddRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.AddResponse] = {
    val futureOperation = request match {
      case rpc.v1.model.UniverseAddRequest(packageName, packageVersion) =>
        packageCollection.getPackageByPackageVersion(packageName, packageVersion)
          .map {
            case (v3Package: universe.v3.model.V3Package, _) =>
              UniverseInstall(v3Package)
            case (v2Package: universe.v3.model.V2Package, _) =>
              throw InvalidPackageVersionForAdd(v2Package.packageCoordinate)
          }
      case rpc.v1.model.UploadAddRequest(packageData) =>
        val packageStream = new ByteArrayInputStream(packageData)
        val packageSize = packageData.length.toLong
        val Some(contentType) = session.contentType

        for {
          stagedPackageId <- stagedPackageStorage.write(packageStream, packageSize, contentType)
          Some(v3Package) <- readPackageDefinitionFromStorage(stagedPackageId)
        } yield {
          Install(stagedPackageId, v3Package)
        }
    }

    for {
      operation <- futureOperation
      _ <- producerView.add(operation.v3Package.packageCoordinate, operation)
    } yield {
      new rpc.v1.model.AddResponse(operation.v3Package)
    }
  }

  private[this] def readPackageDefinitionFromStorage(
    stagedPackageId: UUID
  ): Future[Option[universe.v3.model.V3Package]] = {
    for {
      // TODO package-add: verify media type
      stagedPackageAndMediaType <- stagedPackageStorage.read(stagedPackageId)
      stagedPackage = stagedPackageAndMediaType.map { case (_, inputStream) => inputStream }
      packageMetadata <- traverse(stagedPackage)(extractPackageMetadata)
    } yield {
      // TODO package-add: Get creation time from storage
      packageMetadata.map { packageMetadata =>
        val timeOfAdd = Instant.now().getEpochSecond
        val releaseVersion = universe.v3.model.ReleaseVersion(timeOfAdd).get()
        (packageMetadata, releaseVersion).as[universe.v3.model.V3Package]
      }
    }
  }

  private[this] def traverse[A, B](a: Option[A])(fun: A => Future[B]): Future[Option[B]] = {
    a.map(fun) match {
      case None => Future.value(None)
      case Some(v) => v.map(Some(_))
    }
  }

  private[this] def extractPackageMetadata(inputStream: InputStream): Future[Metadata] = {
    pool(PackageUtil.extractMetadata(inputStream))
      .map(_.valueOr(error => throw InvalidPackage(error)))
  }

}
