package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.OperationInProgress
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.ProducerView
import com.mesosphere.cosmos.storage.installqueue.UniverseInstall
import com.mesosphere.universe
import com.mesosphere.universe.v3.model.PackageDefinitionSpec
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import com.twitter.util.Future
import org.mockito.Mockito._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks

final class PackageAddHandlerSpec extends FreeSpec with MockitoSugar with PropertyChecks {

  import PackageAddHandlerSpec._

  "The PackageAddHandler" - {

    "responds with an error if there's already a package operation pending" - {

      "for Universe add requests" - {

        implicit val session = RequestSession(None, None)

        "with package name only" in {
          forAll (PackageDefinitionSpec.v3PackageGen, genUri) { (packageDef, sourceUri) =>
            assertErrorOnPendingOperation(packageDef, sourceUri, None)
          }
        }

        "with package name and version" in {
          forAll (PackageDefinitionSpec.v3PackageGen, genUri) { (packageDef, sourceUri) =>
            assertErrorOnPendingOperation(packageDef, sourceUri, Some(packageDef.version))
          }
        }

        def assertErrorOnPendingOperation(
          packageDef: universe.v3.model.V3Package,
          sourceUri: Uri,
          packageVersion: Option[universe.v3.model.PackageDefinition.Version]
        ): Unit = {
          val coordinate = rpc.v1.model.PackageCoordinate(packageDef.name, packageDef.version)
          val (packageCollection, stagedPackageStorage, producerView) = buildFixtures

          when(packageCollection.getPackageByPackageVersion(packageDef.name, packageVersion))
            .thenReturn(Future.value((packageDef, sourceUri)))

          when(producerView.add(coordinate, UniverseInstall(packageDef)))
            .thenReturn(Future.exception(OperationInProgress(coordinate)))

          val handler =
            new PackageAddHandler(packageCollection, stagedPackageStorage, producerView)
          val addRequest =
            rpc.v1.model.UniverseAddRequest(packageDef.name, packageVersion)

          assertErrorResponse(handler(addRequest), coordinate)
        }

        def buildFixtures: (PackageCollection, StagedPackageStorage, ProducerView) = {
          val packageCollection = mock[PackageCollection]
          val stagedObjectStorage = mock[ObjectStorage]
          val stagedPackageStorage = StagedPackageStorage(stagedObjectStorage)
          val producerView = mock[ProducerView]

          (packageCollection, stagedPackageStorage, producerView)
        }

        def assertErrorResponse(
          response: Future[rpc.v1.model.AddResponse],
          expectedCoordinate: rpc.v1.model.PackageCoordinate
        ): Unit = {
          val inProgress = intercept[OperationInProgress](Await.result(response))
          assertResult(Status.Conflict)(inProgress.status)
          assertResult(expectedCoordinate)(inProgress.coordinate)
        }

      }

      "for upload add requests" in {
        assert(false)
      }

    }

  }

}

object PackageAddHandlerSpec {

  val genUri: Gen[Uri] = arbitrary[String].map(Uri.parse)

}
