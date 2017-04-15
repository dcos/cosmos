package com.mesosphere.cosmos

import _root_.io.circe.Json
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v2.circe.Decoders._
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.ObjectStorage.ObjectList
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.PackageStorageClient
import com.mesosphere.cosmos.test.InstallQueueFixture
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.universe.{TestUtil => UTestUtil}
import com.mesosphere.util.AbsolutePath
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Buf
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Try
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpec

final class PackageAddSpec
  extends FreeSpec with InstallQueueFixture with BeforeAndAfterAll with BeforeAndAfter {

  import PackageAddSpec._

  "/package/add of an uploaded package file" - {

    "single package" in {
      assertSuccessfulAdd(TestingPackages.MaximalV3ModelV3PackageDefinition)
    }

    "same package coordinate twice" in {
      val expectedV3Package = TestingPackages.MinimalV3ModelV3PackageDefinition
      assertSuccessfulAdd(expectedV3Package)

      // Adding the package again should not overwrite the existing package
      val newV3Package = expectedV3Package.copy(
        description=expectedV3Package.description + " plus some changes"
      )
      assertSuccessfulResponse(newV3Package)

      awaitEmptyInstallQueue()

      // Assert that the externalized state doesn't change
      assertExternalizedPackage(expectedV3Package)
    }
  }

  "/package/add of a universe package" - {

    "by name only" in {
      val addRequest = rpc.v1.model.UniverseAddRequest("cassandra", packageVersion = None)
      assertSuccessfulUniverseAdd(addRequest)
    }

    "by name and version" in {
      val version = universe.v3.model.Version("2.2.5-0.2.0")
      val addRequest = rpc.v1.model.UniverseAddRequest("cassandra", Some(version))
      assertSuccessfulUniverseAdd(addRequest)
    }

    "results in error when trying to add a version 2 package" in {
      val version = universe.v3.model.Version("0.2.0-2")
      val addRequest = rpc.v1.model.UniverseAddRequest("cassandra", Some(version))

      val response = CosmosClient.submit(CosmosRequests.packageAdd(addRequest))
      assertResult(Status.BadRequest)(response.status)
      assertResult(MediaTypes.ErrorResponse)(MediaType.parse(response.contentType.get).get())

      val error = decode[rpc.v1.model.ErrorResponse](response.contentString)
      assertResult(classOf[InvalidPackageVersionForAdd].getSimpleName)(error.`type`)
      assertResult(Some(Json.fromString("cassandra")))(error.data.get("packageName"))
      assertResult(Some(Json.fromString("0.2.0-2")))(error.data.get("packageVersion"))
    }

    def assertSuccessfulUniverseAdd(addRequest: rpc.v1.model.UniverseAddRequest): Assertion = {
      val expectedPackage = describePackage(addRequest.packageName, addRequest.packageVersion)

      val request = CosmosRequests.packageAdd(addRequest)
      val response = CosmosClient.submit(request)

      assertResult(Status.Accepted)(response.status)
      assertResult(MediaTypes.AddResponse)(MediaType.parse(response.contentType.get).get())

      val v3Package = decode[universe.v3.model.V3Package](response.contentString)
      assertResult(expectedPackage) {
        (v3Package: universe.v4.model.PackageDefinition).as[Try[rpc.v2.model.DescribeResponse]].get()
      }

      assertExternalizedPackage(v3Package)
    }

  }

  private[this] var packageObjectStorage: ObjectStorage = _
  private[this] var packageStorage: PackageStorage = _
  private[this] var stagedObjectStorage: ObjectStorage = _
  private[this] var stagedPackageStorage: StagedPackageStorage = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    implicit val statsReceiver: StatsReceiver = NullStatsReceiver

    packageObjectStorage = ObjectStorage.fromUri(PackageStorageClient.packagesUri)
    packageStorage = PackageStorage(packageObjectStorage)

    stagedObjectStorage = ObjectStorage.fromUri(PackageStorageClient.stagedUri)
    stagedPackageStorage = StagedPackageStorage(stagedObjectStorage)
  }

  after {
    cleanObjectStorage(stagedObjectStorage)
    cleanObjectStorage(packageObjectStorage)
  }

  private[this] def assertSuccessfulAdd(
    expectedSupportedPackageDefinition: universe.v4.model.SupportedPackageDefinition
  ): Assertion = {
    assertSuccessfulResponse(expectedSupportedPackageDefinition)
    assertExternalizedPackage(expectedSupportedPackageDefinition)
  }

  private[this] def assertSuccessfulResponse(
    expectedSupportedPackageDefinition: universe.v4.model.SupportedPackageDefinition
  ): Assertion = {
    val body = Buf.ByteArray.Owned(UTestUtil.buildPackage(expectedSupportedPackageDefinition))
    val request = CosmosRequests.packageAdd(body)
    val response = CosmosClient.submit(request)
    assertResult(Status.Accepted)(response.status)
    val actualSupportedPackageDefinition = decode[universe.v4.model.SupportedPackageDefinition](response.contentString)
    assertSamePackage(expectedSupportedPackageDefinition, actualSupportedPackageDefinition)
  }

  private[this] def assertExternalizedPackage(
    expectedSupportedPackageDefinition: universe.v4.model.SupportedPackageDefinition
  ): Assertion = {
    assertSamePackage(
      expectedSupportedPackageDefinition,
      Await.result(
        TestUtil.eventualFuture(
          () => packageStorage.readPackageDefinition(
            expectedSupportedPackageDefinition.packageCoordinate
          )
        )
      )
    )
  }

  private[this] def assertSamePackage(
    expected: universe.v4.model.SupportedPackageDefinition,
    actual: universe.v4.model.SupportedPackageDefinition
  ): Assertion = {
    val normalizedExpected = normalizeSupportedPackageDefinition(expected)
    val normalizedActual = normalizeSupportedPackageDefinition(actual)
    assertResult(normalizedExpected)(normalizedActual)
  }

  private[this] def normalizeSupportedPackageDefinition(
    supportedPackageDefinition: universe.v4.model.SupportedPackageDefinition
  ): universe.v4.model.SupportedPackageDefinition = {
    // TODO package-add: Get release version from creation time in object storage
    val fakeReleaseVersion = universe.v3.model.ReleaseVersion(0L).get()
    supportedPackageDefinition match {
      case v3Package: universe.v3.model.V3Package =>
        v3Package.copy(command = None, releaseVersion = fakeReleaseVersion, selected = None)
      case v4Package: universe.v4.model.V4Package =>
        v4Package.copy(releaseVersion = fakeReleaseVersion, selected = None)
    }
  }

}

object PackageAddSpec {

  def cleanObjectStorage(storage: ObjectStorage): Unit = {
    def cleanObjectList(objectList: ObjectList): Future[Unit] = {
      // Delete all of the objects
      Future.join(objectList.objects.map(storage.delete))
        .before {
          // Delete all of objects in the directories
          Future.join(
            objectList.directories.map { directory =>
              storage.list(directory).flatMap(cleanObjectList)
            }
          )
        }.before {
          // Continue to the next page
          objectList.listToken match {
            case Some(token) => storage.listNext(token).flatMap(cleanObjectList)
            case _ => Future.Done
          }
        }
    }

    Await.result(storage.list(AbsolutePath.Root).flatMap(cleanObjectList))
  }

  def describePackage(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version]
  ): rpc.v2.model.DescribeResponse = {
    val request = CosmosRequests.packageDescribeV2(packageName, packageVersion)
    val response = CosmosClient.submit(request)
    decode[rpc.v2.model.DescribeResponse](response.contentString)
  }

}
