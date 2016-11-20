package com.mesosphere.cosmos

import _root_.io.circe.jawn.decode
import _root_.io.circe.syntax._
import cats.data.Xor
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.AddResponse
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.ObjectStorage.ObjectList
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.Install
import com.mesosphere.cosmos.storage.installqueue.InstallQueue
import com.mesosphere.cosmos.storage.installqueue.PendingOperation
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.PackageStorageClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.ZooKeeperClient
import com.mesosphere.cosmos.test.CosmosRequest
import com.mesosphere.universe
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.bijection.TestUniverseConversions._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.circe.Encoders._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.StreamIO
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.curator.framework.CuratorFramework
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpec

final class PackageAddSpec extends FreeSpec with BeforeAndAfterAll with BeforeAndAfter {

  import PackageAddSpec._

  "/package/add responds with a 202" - {

    "single package" in {
      assertSuccessfulAdd(TestingPackages.MaximalV3ModelV3PackageDefinition)
    }

    "identical packages are written to different staging locations" in {

      def addPackageAndGetId(packageData: universe.v3.model.Metadata): UUID = {
        val packageBytes = buildPackage(packageData)
        val request = packageAddRequest(packageBytes)
        val _ = CosmosClient.submit(request)

        val PendingOperation(_, operation, _) = popOperationFromInstallQueue()
        val Install(stagedPackageId, _) = operation

        stagedPackageId
      }

      val firstId = addPackageAndGetId(TestingPackages.MinimalV3ModelMetadata)
      val secondId = addPackageAndGetId(TestingPackages.MinimalV3ModelMetadata)

      assert(firstId != secondId)
    }

  }

  private[this] var zkClient: CuratorFramework = _
  private[this] var installQueue: InstallQueue = _
  private[this] var stagedObjectStorage: ObjectStorage = _
  private[this] var stagedPackageStorage: StagedPackageStorage = _

  override def beforeAll(): Unit = {
    implicit val statsReceiver: StatsReceiver = NullStatsReceiver

    zkClient = zookeeper.Clients.createAndInitialize(ZooKeeperClient.uri)
    installQueue = InstallQueue(zkClient)

    stagedObjectStorage = ObjectStorage.fromUri(PackageStorageClient.stagedUri)
    stagedPackageStorage = StagedPackageStorage(stagedObjectStorage)
  }

  override def afterAll(): Unit = {
    installQueue.close()
    zkClient.close()
  }

  after {
    cleanInstallQueue(installQueue)
    cleanStagedPackageStorage(stagedObjectStorage)
  }

  type PackageMetadata = universe.v3.model.Metadata
  type ReleaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion

  private[this] def assertSuccessfulAdd(expectedV3Package: universe.v3.model.V3Package): Unit = {
    val (expectedMetadata, _) = expectedV3Package.as[(PackageMetadata, ReleaseVersion)]
    val expectedPackageBytes = buildPackage(expectedMetadata)
    val request = packageAddRequest(expectedPackageBytes)
    val response = CosmosClient.submit(request)

    assertValidResponse(response, expectedV3Package)

    val PendingOperation(coordinate, operation, failure) = popOperationFromInstallQueue()
    val Install(stagedPackageId, v3Package) = operation

    assertResult(expectedV3Package.name)(coordinate.name)
    assertResult(expectedV3Package.version)(coordinate.version)
    assertSamePackage(expectedV3Package, v3Package)
    assert(failure.isEmpty)

    assertObjectStorageInExpectedState(stagedPackageId, expectedPackageBytes)
  }

  private[this] def assertValidResponse(
    response: Response,
    expectedV3Package: universe.v3.model.V3Package
  ): Unit = {
    assertResult(Status.Accepted)(response.status)
    val Xor.Right(addResponse) = decode[AddResponse](response.contentString)
    assertSamePackage(expectedV3Package, addResponse.v3Package)
  }

  private[this] def popOperationFromInstallQueue(): PendingOperation = {
    Await.result {
      installQueue.next().flatMap {
        case Some(operation) =>
          installQueue.success(
            operation.packageCoordinate
          ) before (
            Future.value(operation)
          )
        case _ => Future.exception(new AssertionError("Pending operation expected"))
      }
    }
  }

  // TODO package-add: this will no longer work once the processor handles installs
  private[this] def assertObjectStorageInExpectedState(
    stagedPackageId: UUID,
    expectedPackage: Array[Byte]
  ): Unit = {
    val (mediaType, inputStream) = Await.result(stagedPackageStorage.get(stagedPackageId))
    val actualPackage = StreamIO.buffer(inputStream).toByteArray
    assertResult(MediaTypes.PackageZip)(mediaType)
    assertResult(expectedPackage)(actualPackage)
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

  def packageAddRequest(packageData: Array[Byte]): CosmosRequest = {
    val packageBytes = new ByteArrayInputStream(packageData)

    CosmosRequest.post(
      path = "package/add",
      body = packageBytes,
      contentType = MediaTypes.PackageZip,
      accept = MediaTypes.universeV3Package,
      customHeaders = Map("X-Dcos-Content-Length" -> packageData.length.toString)
    )
  }

  def buildPackage(packageData: universe.v3.model.Metadata): Array[Byte] = {
    // TODO package-add: Factor out common Zip-handling code into utility methods
    val bytesOut = new ByteArrayOutputStream()
    val packageOut = new ZipOutputStream(bytesOut, StandardCharsets.UTF_8)
    packageOut.putNextEntry(new ZipEntry("metadata.json"))
    packageOut.write(packageData.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    packageOut.closeEntry()
    packageOut.close()

    bytesOut.toByteArray
  }

  def cleanInstallQueue(installQueue: InstallQueue): Unit = {
    def loop: Future[Unit] = {
      installQueue.next().flatMap {
        case Some(operation) => installQueue.success(operation.packageCoordinate).before(loop)
        case _ => Future.Done
      }
    }

    Await.result(loop)
  }

  def cleanStagedPackageStorage(storage: ObjectStorage): Unit = {
    def cleanObjectList(objectList: ObjectList): Future[Unit] = {
      Future.join(objectList.objects.map(storage.delete))
        .before {
          objectList.listToken match {
            case Some(token) => storage.listNext(token).flatMap(cleanObjectList)
            case _ => Future.Done
          }
        }
    }

    // TODO package-add: Be more lenient about slashes at beginning and end of paths
    Await.result(storage.list("").flatMap(cleanObjectList))
  }

}
