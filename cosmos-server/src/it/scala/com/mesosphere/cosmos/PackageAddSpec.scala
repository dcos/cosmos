package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.http.CosmosRequest
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v2.circe.Decoders._
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.ObjectStorage.ObjectList
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.InstallQueue
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.PackageStorageClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.ZooKeeperClient
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe
import com.mesosphere.universe.bijection.TestUniverseConversions._
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.universe.{MediaTypes => UMediaTypes}
import com.mesosphere.universe.{TestUtil => UTestUtil}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Buf
import com.twitter.util.Await
import com.twitter.util.Future
import org.apache.curator.framework.CuratorFramework
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpec

final class PackageAddSpec extends FreeSpec with BeforeAndAfterAll with BeforeAndAfter {

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

      // Wait until the install queue is empty
      Await.result(TestUtil.eventualFutureNone(installQueue.next))

      // Assert that the externalized state doesn't change
      assertSamePackage(
        expectedV3Package,
        Await.result(
          TestUtil.eventualFuture(
            () => packageStorage.readPackageDefinition(
              expectedV3Package.packageCoordinate
            )
          )
        )
      )
    }
  }

  "/package/add of a universe package" - {

    "by name only" in {
      val addRequest = rpc.v1.model.UniverseAddRequest("cassandra", packageVersion = None)
      assertSuccessfulUniverseAdd(addRequest)
    }

    // TODO package-add: Fix this to not use a v2Package
    "by name and version" ignore {
      val version = universe.v3.model.PackageDefinition.Version("0.2.0-2")
      val addRequest = rpc.v1.model.UniverseAddRequest("cassandra", Some(version))
      assertSuccessfulUniverseAdd(addRequest)
    }

    def assertSuccessfulUniverseAdd(addRequest: rpc.v1.model.UniverseAddRequest): Unit = {
      val expectedPackage = describePackage(addRequest.packageName, addRequest.packageVersion)

      val request = packageAddRequest(addRequest)
      val response = CosmosClient.submit(request)

      assertResult(Status.Accepted)(response.status)
      assertResult(MediaTypes.AddResponse)(MediaType.parse(response.contentType.get).get())
      assertResult(expectedPackage) {
        val decoded = decode[rpc.v1.model.AddResponse](response.contentString)
        decoded.packageDefinition.as[rpc.v2.model.DescribeResponse]
      }

      // TODO package-add: Need different assertion
      //assertInstallOperationInZk(expectedPackage)
    }

  }

  private[this] var zkClient: CuratorFramework = _
  private[this] var installQueue: InstallQueue = _
  private[this] var packageObjectStorage: ObjectStorage = _
  private[this] var packageStorage: PackageObjectStorage = _
  private[this] var stagedObjectStorage: ObjectStorage = _
  private[this] var stagedPackageStorage: StagedPackageStorage = _

  override def beforeAll(): Unit = {
    implicit val statsReceiver: StatsReceiver = NullStatsReceiver

    zkClient = zookeeper.Clients.createAndInitialize(ZooKeeperClient.uri)

    installQueue = InstallQueue(zkClient)

    packageObjectStorage = ObjectStorage.fromUri(PackageStorageClient.packagesUri)
    packageStorage = PackageObjectStorage(packageObjectStorage)

    stagedObjectStorage = ObjectStorage.fromUri(PackageStorageClient.stagedUri)
    stagedPackageStorage = StagedPackageStorage(stagedObjectStorage)
  }

  override def afterAll(): Unit = {
    installQueue.close()
    zkClient.close()
  }

  after {
    cleanObjectStorage(stagedObjectStorage)
    cleanObjectStorage(packageObjectStorage)
  }

  // TODO package-add: Is this needed anymore?
  // TODO package-add: Make this similar to the equivalent upload-install assertions
//  private[this] def assertInstallOperationInZk(expected: rpc.v2.model.DescribeResponse): Unit = {
//    val PendingOperation(coordinate, operation, failure) = popOperationFromInstallQueue()
//    val UniverseInstall(packageDefinition) = operation
//
//    assertResult(expected.name)(coordinate.name)
//    assertResult(expected.version)(coordinate.version)
//    assert(failure.isEmpty)
//    assertResult(expected)(packageDefinition.as[rpc.v2.model.DescribeResponse])
//  }

  type PackageMetadata = universe.v3.model.Metadata
  type ReleaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion

  private[this] def assertSuccessfulAdd(expectedV3Package: universe.v3.model.V3Package): Unit = {
    assertSuccessfulResponse(expectedV3Package)

    // Assert that we externalize the correct state
    assertSamePackage(
      expectedV3Package,
      Await.result(
        TestUtil.eventualFuture(
          () => packageStorage.readPackageDefinition(
            expectedV3Package.packageCoordinate
          )
        )
      )
    )
  }

  private[this] def assertSuccessfulResponse(
    expectedV3Package: universe.v3.model.V3Package
  ): Unit = {
    val (expectedMetadata, _) = expectedV3Package.as[(PackageMetadata, ReleaseVersion)]

    val request = packageAddRequest(Buf.ByteArray.Owned(UTestUtil.buildPackage(expectedMetadata)))
    val response = CosmosClient.submit(request)
    assertResult(Status.Accepted)(response.status)
    val actualV3Package = decode[universe.v3.model.V3Package](response.contentString)
    assertSamePackage(expectedV3Package, actualV3Package)
  }

  private[this] def assertSamePackage(
    expected: universe.v3.model.V3Package,
    actual: universe.v3.model.V3Package
  ): Unit = {
    // TODO package-add: Get release version from creation time in object storage
    val fakeReleaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0L).get()
    val normalizedExpected = expected.copy(
      command = None,
      releaseVersion = fakeReleaseVersion,
      selected = None
    )
    val normalizedActual = actual.copy(releaseVersion = fakeReleaseVersion)

    assertResult(normalizedExpected)(normalizedActual)
  }

}

object PackageAddSpec {

  def packageAddRequest(packageData: Buf): CosmosRequest = {
    CosmosRequest.post(
      path = "package/add",
      body = packageData,
      contentType = UMediaTypes.PackageZip,
      accept = MediaTypes.AddResponse
    )
  }

  def packageAddRequest(requestBody: rpc.v1.model.UniverseAddRequest): CosmosRequest = {
    CosmosRequest.post(
      path = "package/add",
      body = requestBody,
      contentType = MediaTypes.AddRequest,
      accept = MediaTypes.AddResponse
    )
  }

  def packageDescribeRequest(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): CosmosRequest = {
    val oldVersion = packageVersion.as[Option[universe.v2.model.PackageDetailsVersion]]

    CosmosRequest.post(
      path = "package/describe",
      body = rpc.v1.model.DescribeRequest(packageName, oldVersion),
      contentType = MediaTypes.DescribeRequest,
      accept = MediaTypes.V2DescribeResponse
    )
  }

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

    // TODO package-add: Be more lenient about slashes at beginning and end of paths
    Await.result(storage.list("").flatMap(cleanObjectList))
  }

  def describePackage(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): rpc.v2.model.DescribeResponse = {
    val request = packageDescribeRequest(packageName, packageVersion)
    val response = CosmosClient.submit(request)
    decode[rpc.v2.model.DescribeResponse](response.contentString)
  }

}
