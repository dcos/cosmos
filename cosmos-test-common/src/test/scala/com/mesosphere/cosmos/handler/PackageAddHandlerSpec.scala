package com.mesosphere.cosmos.handler

import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.OperationInProgress
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.UploadAddRequest
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.ProducerView
import com.mesosphere.cosmos.storage.v1.model.Operation
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.TestUtil
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.util.AbsolutePath
import com.mesosphere.util.PackageUtil
import com.mesosphere.util.PackageUtil.PackageError
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.PropertyChecks

final class PackageAddHandlerSpec
extends FreeSpec
with Matchers
with MockitoSugar
with PropertyChecks {

  "The PackageAddHandler" - {

    "responds with an error if there's already a package operation pending" - {

      "for Universe add requests" - {

        implicit val session = RequestSession(None, None)

        "with package name only" in {
          forAll { (packageDef: universe.v4.model.SupportedPackageDefinition, sourceUri: Uri) =>
            assertErrorOnPendingOperation(packageDef, sourceUri, None)
          }
        }

        "with package name and version" in {
          forAll { (packageDef: universe.v4.model.SupportedPackageDefinition, sourceUri: Uri) =>
            assertErrorOnPendingOperation(packageDef, sourceUri, Some(packageDef.version))
          }
        }

        def assertErrorOnPendingOperation(
          packageDef: universe.v4.model.SupportedPackageDefinition,
          sourceUri: Uri,
          packageVersion: Option[universe.v3.model.Version]
        ): Assertion = {
          val coordinate = rpc.v1.model.PackageCoordinate(packageDef.name, packageDef.version)
          val addRequest = rpc.v1.model.UniverseAddRequest(packageDef.name, packageVersion)

          val handler = buildHandler { (packageCollection, _, producerView) =>
            when(packageCollection.getPackageByPackageVersion(packageDef.name, packageVersion))
              .thenReturn(Future.value((packageDef, sourceUri)))

            when(producerView.add(coordinate, UniverseInstall(packageDef)))
              .thenReturn(Future.exception(OperationInProgress(coordinate).exception))
          }

          assertErrorResponse(handler(addRequest), coordinate)
        }

      }

      "for upload add requests" in {
        implicit val session = RequestSession(None, Some(MediaTypes.PackageZip))

        forAll { (packageDef: universe.v4.model.SupportedPackageDefinition) =>
          val packageData = TestUtil.buildPackage(packageDef)
          val addRequest = rpc.v1.model.UploadAddRequest(packageData)

          val handler = buildHandler { (_, stagedObjectStorage, producerView) =>
            when {
              stagedObjectStorage
                .write(any[AbsolutePath], any[InputStream], any[Long], any[MediaType])
            }.thenReturn(Future.Unit)

            when(stagedObjectStorage.read(any[AbsolutePath])).thenReturn {
              Future.value(Some((MediaTypes.PackageZip, new ByteArrayInputStream(packageData))))
            }

            when(producerView.add(any[rpc.v1.model.PackageCoordinate], any[Operation])).thenReturn(
              Future.exception(OperationInProgress(packageDef.packageCoordinate).exception)
            )
          }

          assertErrorResponse(handler(addRequest), packageDef.packageCoordinate)
        }
      }

      def assertErrorResponse(
        response: Future[rpc.v1.model.AddResponse],
        expectedCoordinate: rpc.v1.model.PackageCoordinate
      ): Assertion = {
        val exception = intercept[CosmosException](Await.result(response))

        exception.status shouldBe Status.Conflict
        exception.error.asInstanceOf[OperationInProgress].coordinate shouldBe expectedCoordinate
      }

    }

    "responds with an error if the package is invalid" in {
      implicit val session = RequestSession(None, Some(MediaTypes.universeV3Package))

      forAll { (uploadAddRequest: UploadAddRequest) =>
        val packageIn = new ByteArrayInputStream(uploadAddRequest.packageData)
        val expectedResult = PackageUtil.extractMetadata(packageIn)

        whenever (expectedResult.isLeft) {
          val Left(expectedError) = expectedResult
          val handler = buildHandler { (_, stagedObjectStorage, _) =>
            when {
              stagedObjectStorage
                .write(any[AbsolutePath], any[InputStream], any[Long], any[MediaType])
            }.thenReturn(Future.Unit)

            val packageFromStorage = new ByteArrayInputStream(uploadAddRequest.packageData)
            when(stagedObjectStorage.read(any[AbsolutePath])).thenReturn {
              Future.value(Some((MediaTypes.universeV3Package, packageFromStorage)))
            }
          }

          val response = handler(uploadAddRequest)
          val exception = intercept[CosmosException](Await.result(response))

          exception.error shouldBe a[PackageError]
          exception.error.asInstanceOf[PackageError] shouldBe expectedError
        }
      }
    }

    def buildHandler(
      configureMocks: (PackageCollection, ObjectStorage, ProducerView) => Any
    ): PackageAddHandler = {
      val packageCollection = mock[PackageCollection]
      val stagedObjectStorage = mock[ObjectStorage]
      val producerView = mock[ProducerView]

      configureMocks(packageCollection, stagedObjectStorage, producerView)

      val stagedPackageStorage = StagedPackageStorage(stagedObjectStorage)
      new PackageAddHandler(packageCollection, stagedPackageStorage, producerView)
    }

  }

}
