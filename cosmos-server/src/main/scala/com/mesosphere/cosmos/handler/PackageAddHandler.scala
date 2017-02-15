package com.mesosphere.cosmos.handler

import cats.implicits._
import com.mesosphere.cosmos.InvalidPackageVersionForAdd
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.ProducerView
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe
import com.mesosphere.universe.bijection.BijectionUtils.twitterTryToScalaTry
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.Try
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

final class PackageAddHandler(
  packageCollection: PackageCollection,
  stagedPackageStorage: StagedPackageStorage,
  producerView: ProducerView
) extends EndpointHandler[rpc.v1.model.AddRequest, rpc.v1.model.AddResponse](
  successStatus = Status.Accepted
) {

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

  private[this] def invert[A](a: Option[Try[A]]): Try[Option[A]] = {
    // silly function to change types
    Try.fromScala(a.map(twitterTryToScalaTry).sequenceU)
  }

  private[this] def readPackageDefinitionFromStorage(
    stagedPackageId: UUID
  ): Future[Option[universe.v3.model.V3Package]] = {
    for {
      // TODO package-add: verify media type
      stagedPackage <- stagedPackageStorage.read(stagedPackageId)
      packageInputStream = stagedPackage.map{ case (_, inputStream) => inputStream}
      packageZip = packageInputStream.map(new ZipInputStream(_))
      packageMetadata <- Future.const(invert(packageZip.map(extractPackageMetadata)))
    } yield {
      // TODO package-add: Get creation time from storage
      packageMetadata.map { packageMetadata =>
        val timeOfAdd = Instant.now().getEpochSecond
        val releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(timeOfAdd).get()
        (packageMetadata, releaseVersion).as[universe.v3.model.V3Package]
      }
    }
  }

  private[this] def extractPackageMetadata(
    packageZip: ZipInputStream
  ): Try[universe.v3.model.Metadata] = {
    Try {
      // TODO package-add: Factor out common Zip-handling code into utility methods
      Iterator
        .continually(Option(packageZip.getNextEntry()))
        .takeWhile(_.isDefined)
        .flatten
        // TODO package-add: Test cases for files with unexpected content
        .filter(_.getName == "metadata.json")
        .map { _ =>
          val metadataBytes = StreamIO.buffer(packageZip).toByteArray
          decode[universe.v3.model.Metadata](new String(metadataBytes, StandardCharsets.UTF_8))
        }
        .next()
    } ensure packageZip.close()
  }

}
