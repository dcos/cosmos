package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.InstallQueueError
import com.mesosphere.cosmos.error.OperationInProgress
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.cosmos.model.ZooKeeperUri
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.storage.v1.model.FailedStatus
import com.mesosphere.cosmos.storage.v1.model.OperationFailure
import com.mesosphere.cosmos.storage.v1.model.OperationStatus
import com.mesosphere.cosmos.storage.v1.model.PendingOperation
import com.mesosphere.cosmos.storage.v1.model.PendingStatus
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.cosmos.zookeeper.Clients
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.twitter.util.Await
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.test.TestingCluster
import org.apache.zookeeper.KeeperException
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.Outcome
import org.scalatest.fixture
import org.scalatest.prop.TableDrivenPropertyChecks

final class InstallQueueSpec
extends fixture.FreeSpec
with BeforeAndAfterAll
with Matchers
with TableDrivenPropertyChecks {

  import InstallQueue._
  import InstallQueueSpec._

  private[this] var zkCluster: TestingCluster = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    zkCluster = new TestingCluster(1)
    zkCluster.start()
  }

  override def afterAll(): Unit = {
    zkCluster.close()

    super.afterAll()
  }

  // scalastyle:off method.length
  def specWithVersionedUniverseInstall(universeInstall: UniverseInstall): Unit = {
    "Producer View" - {
      "Add an operation " - {
        "when no parent path exists" in { testParameters =>
          val (client, installQueue) = testParameters
          val addResult = Await.result(
            installQueue.add(coordinate1, universeInstall)
          )
          assertResult(())(addResult)

          checkInstallQueueContents(client, coordinate1, PendingStatus(universeInstall, None))
        }

        "when the parent path exists but the status does not" in { testParameters =>
          val (client, installQueue) = testParameters
          createParentPath(client)
          val addResult = Await.result(
            installQueue.add(coordinate1, universeInstall)
          )
          assertResult(())(addResult)

          checkInstallQueueContents(
            client,
            coordinate1,
            PendingStatus(universeInstall, None))
        }

        "on a coordinate that has a pending operation but no failures" in { testParameters =>
          val (client, installQueue) = testParameters
          val pendingUniverseInstall = PendingStatus(universeInstall, None)

          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            pendingUniverseInstall)

          val exception = intercept[CosmosException] {
            Await.result(installQueue.add(coordinate1, universeInstall))
          }
          exception.error shouldBe OperationInProgress(coordinate1)

          checkInstallQueueContents(client,
            coordinate1,
            pendingUniverseInstall)
        }

        "on a coordinate that has a failed operation, but no pending operation" in { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            FailedStatus(OperationFailure(universeInstall, errorResponse1))
          )

          val addResult = Await.result(
            installQueue.add(coordinate1, universeInstall)
          )
          assertResult(())(addResult)

          checkInstallQueueContents(
            client,
            coordinate1,
            PendingStatus(
              universeInstall,
              Some(OperationFailure(universeInstall, errorResponse1))
            )
          )
        }

        "on a coordinate that has an operation and a failure" in { testParameters =>
          val (client, installQueue) = testParameters

          val pendingUniverseInstallWithFailure = PendingStatus(
            universeInstall,
            Some(OperationFailure(universeInstall, errorResponse1))
          )

          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            pendingUniverseInstallWithFailure)

          val exception = intercept[CosmosException] {
            Await.result(installQueue.add(coordinate1, universeInstall))
          }
          exception.error shouldBe OperationInProgress(coordinate1)

          checkInstallQueueContents(
            client,
            coordinate1,
            pendingUniverseInstallWithFailure
          )
        }
      }

      "Add multiple non-conflicting operations" in { testParameters =>
        val (client, installQueue) = testParameters
        val addTwoOperations =
          for {
            add1 <- installQueue.add(coordinate1, universeInstall)
            add2 <- installQueue.add(coordinate2, universeInstall)
          } yield (add1, add2)

        val (add1, add2) = Await.result(addTwoOperations)
        assertResult(())(add1)
        assertResult(())(add2)

        checkInstallQueueContents(client, coordinate1, PendingStatus(universeInstall, None))
        checkInstallQueueContents(client, coordinate2, PendingStatus(universeInstall, None))
      }
    }

    "Processor view" - {
      "failure" - {
        "Fail an operation " - {
          "when no parent path exists" in { testParameters =>
            val (_, installQueue) = testParameters
            val exception = intercept[CosmosException](
              Await.result(
                installQueue.failure(coordinate1, errorResponse1)
              )
            )

            exception.error shouldBe a[InstallQueueError]
            assertResult(notInQueueFailureMessageCoordinate1)(exception.error.message)
          }

          "when the parent path exists but the status does not" in { testParameters =>
            val (client, installQueue) = testParameters
            createParentPath(client)
            val exception = intercept[CosmosException](
              Await.result(
                installQueue.failure(coordinate1, errorResponse1)
              )
            )

            exception.error shouldBe a[InstallQueueError]
            assertResult(notInQueueFailureMessageCoordinate1)(exception.error.message)
          }

          "on a coordinate that has a pending operation but no failures" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(client, coordinate1, PendingStatus(universeInstall, None))
            Await.result(
              installQueue.failure(coordinate1, errorResponse1)
            )
            checkInstallQueueContents(
              client,
              coordinate1,
              FailedStatus(OperationFailure(universeInstall, errorResponse1)))
          }

          "on a coordinate that has a failed operation, but no pending operation" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              FailedStatus(OperationFailure(universeInstall, errorResponse1)))
            val exception = intercept[CosmosException](
              Await.result(
                installQueue.failure(coordinate1, errorResponse1)
              )
            )

            exception.error shouldBe a[InstallQueueError]
            assertResult(alreadyFailedFailureMessageCoordinate1)(exception.error.message)
          }

          "on a coordinate that has an operation and a failure" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              PendingStatus(
                universeInstall,
                Some(OperationFailure(universeInstall, errorResponse1))
              )
            )
            Await.result(
              installQueue.failure(coordinate1, errorResponse2)
            )
            checkInstallQueueContents(
              client,
              coordinate1,
              FailedStatus(OperationFailure(universeInstall, errorResponse2)))
          }
        }

        "Fail multiple non-conflicting operations" in { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(client, coordinate1, PendingStatus(universeInstall, None))
          insertPackageStatusIntoQueue(client, coordinate2, PendingStatus(universeInstall, None))
          val failTwoOperations =
            for {
              _ <- installQueue.failure(coordinate1, errorResponse1)
              _ <- installQueue.failure(coordinate2, errorResponse1)
            } yield ()
          Await.result(failTwoOperations)
          checkInstallQueueContents(
            client,
            coordinate1,
            FailedStatus(OperationFailure(universeInstall, errorResponse1)))
          checkInstallQueueContents(
            client,
            coordinate2,
            FailedStatus(OperationFailure(universeInstall, errorResponse1)))
        }
      }

      "success" - {
        "Success on an operation " - {
          "when no parent path exists" in { testParameters =>
            val (_, installQueue) = testParameters
            val exception = intercept[CosmosException](
              Await.result(
                installQueue.success(coordinate1)
              )
            )

            exception.error shouldBe a[InstallQueueError]
            assertResult(notInQueueSuccessMessageCoordinate1)(exception.error.message)
          }

          "when the parent path exists but the status does not" in { testParameters =>
            val (client, installQueue) = testParameters
            createParentPath(client)
            val exception = intercept[CosmosException](
              Await.result(
                installQueue.success(coordinate1)
              )
            )

            exception.error shouldBe a[InstallQueueError]
            assertResult(notInQueueSuccessMessageCoordinate1)(exception.error.message)
          }

          "on a coordinate that has a pending operation but no failures" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(client, coordinate1, PendingStatus(universeInstall, None))
            Await.result(
              installQueue.success(coordinate1)
            )
            checkStatusDoesNotExist(client, coordinate1)
          }

          "on a coordinate that has a failed operation, but no pending operation" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              FailedStatus(OperationFailure(universeInstall, errorResponse1)))
            val exception = intercept[CosmosException](
              Await.result(
                installQueue.success(coordinate1)
              )
            )

            exception.error shouldBe a[InstallQueueError]
            assertResult(alreadyFailedSuccessMessageCoordinate1)(exception.error.message)
          }

          "on a coordinate that has an operation and a failure" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              PendingStatus(
                universeInstall,
                Some(OperationFailure(universeInstall, errorResponse1))
              )
            )
            Await.result(
              installQueue.success(coordinate1)
            )
            checkStatusDoesNotExist(client, coordinate1)
          }
        }

        "Success on multiple non-conflicting operations" in { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(client, coordinate1, PendingStatus(universeInstall, None))
          insertPackageStatusIntoQueue(client, coordinate2, PendingStatus(universeInstall, None))
          val successOnTwoOperations =
            for {
              _ <- installQueue.success(coordinate1)
              _ <- installQueue.success(coordinate2)
            } yield ()
          Await.result(successOnTwoOperations)
          checkStatusDoesNotExist(client, coordinate1)
          checkStatusDoesNotExist(client, coordinate2)
        }
      }

      "next" - {
        "Next when " - {
          "no parent path exists" in { testParameters =>
            val (_, installQueue) = testParameters
            val nextPendingOperation = Await.result(
              installQueue.next()
            )
            assertResult(None)(nextPendingOperation)
          }

          "the parent path exists but there are no pending operations" in { testParameters =>
            val (client, installQueue) = testParameters
            createParentPath(client)
            val nextPendingOperation = Await.result(
              installQueue.next()
            )
            assertResult(None)(nextPendingOperation)
          }

          "there is a coordinate that has a pending operation but no failures" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              PendingStatus(universeInstall, None))
            val nextPendingOperation = Await.result(
              installQueue.next()
            )
            assertResult(
              Some(PendingOperation(universeInstall, None)))(
              nextPendingOperation)
          }

          "there is a coordinate that has a failed operation, but no pending operation" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              FailedStatus(OperationFailure(universeInstall, errorResponse1)))
            val nextPendingOperation = Await.result(
              installQueue.next()
            )
            assertResult(None)(nextPendingOperation)
          }

          "there is a coordinate that has an operation and a failure" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              PendingStatus(
                universeInstall,
                Some(OperationFailure(
                  universeInstall, errorResponse1))))
            val nextPendingOperation = Await.result(
              installQueue.next()
            )
            assertResult(
              Some(PendingOperation(
                universeInstall,
                Some(OperationFailure(
                  universeInstall, errorResponse1)))))(
              nextPendingOperation)
          }

          "there are multiple pending operations some of which have failed" in { testParameters =>
            val (client, installQueue) = testParameters
            insertPackageStatusIntoQueue(
              client,
              coordinate1,
              PendingStatus(universeInstall, None))
            insertPackageStatusIntoQueue(
              client,
              coordinate2,
              PendingStatus(universeInstall, None))
            insertPackageStatusIntoQueue(
              client,
              coordinate3,
              FailedStatus(OperationFailure(universeInstall, errorResponse1)))
            insertPackageStatusIntoQueue(
              client,
              coordinate4,
              FailedStatus(OperationFailure(universeInstall, errorResponse2)))
            insertPackageStatusIntoQueue(
              client,
              coordinate5,
              PendingStatus(universeInstall, Some(OperationFailure(universeInstall, errorResponse1))))

            val n1 = Await.result(installQueue.next())
            removePackageStatusFromQueue(client, coordinate1)

            val n2 = Await.result(installQueue.next())
            removePackageStatusFromQueue(client, coordinate2)

            val n3 = Await.result(installQueue.next())
            removePackageStatusFromQueue(client, coordinate5)

            val n4 = Await.result(installQueue.next())
            val n5 = Await.result(installQueue.next())

            assertResult(
              Some(PendingOperation(universeInstall, None)))(
              n1)
            assertResult(
              Some(PendingOperation(universeInstall, None)))(
              n2)
            assertResult(
              Some(PendingOperation(
                universeInstall,
                Some(OperationFailure(universeInstall, errorResponse1)))))(
              n3)
            assertResult(None)(n4)
            assertResult(None)(n5)
          }
        }

        "Calling next multiple times returns the same operation" in { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            PendingStatus(universeInstall, None))
          insertPackageStatusIntoQueue(
            client,
            coordinate2,
            PendingStatus(universeInstall, None))
          insertPackageStatusIntoQueue(
            client,
            coordinate3,
            FailedStatus(OperationFailure(universeInstall, errorResponse1)))
          insertPackageStatusIntoQueue(
            client,
            coordinate4,
            FailedStatus(OperationFailure(universeInstall, errorResponse1)))
          insertPackageStatusIntoQueue(
            client,
            coordinate5,
            PendingStatus(universeInstall, Some(OperationFailure(universeInstall, errorResponse1))))

          val callNextTwice =
            for {
              n1 <- installQueue.next()
              n2 <- installQueue.next()
            } yield (n1, n2)
          val (n1, n2) = Await.result(callNextTwice)

          checkInstallQueueContents(client, coordinate1, PendingStatus(universeInstall, None))
          removePackageStatusFromQueue(client, coordinate1)

          val callNextTwiceAgain =
            for {
              n3 <- installQueue.next()
              n4 <- installQueue.next()
            } yield (n3, n4)
          val (n3, n4) = Await.result(callNextTwiceAgain)

          assert(n1 == n2)
          assert(n3 == n4)
          assertResult(
            Some(PendingOperation(universeInstall, None)))(
            n1)
          assertResult(
            Some(PendingOperation(universeInstall, None)))(
            n3)
        }
      }
    }

    "Reader view" - {
      "ViewStatus should " +
        "return all pending and failed operations in the queue" in { testParameters =>
        val (client, installQueue) = testParameters
        val expectedState = Map(
          coordinate1 -> PendingStatus(universeInstall, None),
          coordinate2 -> PendingStatus(universeInstall, None),
          coordinate3 -> FailedStatus(OperationFailure(universeInstall, errorResponse1)),
          coordinate4 -> FailedStatus(OperationFailure(universeInstall, errorResponse1)),
          coordinate5 -> PendingStatus(universeInstall, Some(OperationFailure(universeInstall, errorResponse1)))
        )

        expectedState.foreach { case (coordinate, status) =>
          insertPackageStatusIntoQueue(client, coordinate, status)
        }

        val pollCount = 10
        val actualState = pollForViewStatus(installQueue, pollCount, expectedState.size)

        assertResult(Some(expectedState))(actualState)
      }

      "return an empty map when the parent path has not been created" in { testParameters =>
        val (_, installQueue) = testParameters
        val status = Await.result(installQueue.viewStatus())
        assertResult(Map())(status)
      }

      "return an empty map when the parent path" +
        " has been created but there are no operations" in { testParameters =>
        val (client, installQueue) = testParameters
        createParentPath(client)
        val status = Await.result(installQueue.viewStatus())
        assertResult(Map())(status)
      }
    }

    "Install Queue" - {
      "When an operation is added on a failed coordinate," +
        " that coordinate must move to the back of the queue" in { testParameters =>
        val (_, installQueue) = testParameters
        val addOnCoordinate1 =
          Await.result(installQueue.add(coordinate1, universeInstall))
        assertResult(())(addOnCoordinate1)

        val addOnCoordinate2 =
          Await.result(installQueue.add(coordinate2, universeInstall))
        assertResult(())(addOnCoordinate2)

        val coordinate1Operation = Await.result(installQueue.next())
        assertResult(
          Some(PendingOperation(universeInstall, None)))(
          coordinate1Operation)

        Await.result(installQueue.failure(coordinate1, errorResponse1))
        val addOnCoordinate1AfterFailure =
          Await.result(installQueue.add(coordinate1, universeInstall))
        assertResult(())(addOnCoordinate1AfterFailure)

        val coordinate2Operation = Await.result(installQueue.next())
        assertResult(
          Some(
            PendingOperation(
              universeInstall,
              None)))(
          coordinate2Operation)
      }
    }
  }
  // scalastyle:on

  "install queue storing" - {
    "v3 packages should" - {
      behave like specWithVersionedUniverseInstall(universeInstall)
    }
    "v4 packages should" - {
      behave like specWithVersionedUniverseInstall(universeInstallV4)
    }
  }

  private implicit val stats = com.twitter.finagle.stats.NullStatsReceiver
  type FixtureParam = (CuratorFramework, InstallQueue)

  override def withFixture(test: OneArgTest): Outcome = {
    val namespace = getClass.getSimpleName
    val zkUri = ZooKeeperUri.parse(s"zk://${zkCluster.getConnectString}/$namespace").get()
    val client = Clients.createAndInitialize(zkUri)
    client.getZookeeperClient.blockUntilConnectedOrTimedOut()

    try {
      withFixture(test.toNoArgTest((client, InstallQueue(client))))
    } finally {
      try {
        client.delete().deletingChildrenIfNeeded().forPath(installQueuePath)
        ()
      } catch {
        case _: KeeperException.NoNodeException =>
      }

      client.close()
    }
  }

  private def checkInstallQueueContents
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate,
    expected: OperationStatus
  ): Assertion = {
    val data =
      client
        .getData
        .forPath(
          statusPath(coordinate1)
        )
    val operationStatus = StorageEnvelope.decodeData[OperationStatus](data)
    assertResult(expected)(operationStatus)
  }

  private def insertPackageStatusIntoQueue
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate,
    contents: OperationStatus
  ): Unit = {
    client
      .create()
      .creatingParentsIfNeeded()
      .forPath(
        statusPath(packageCoordinate),
        StorageEnvelope.encodeData(contents)
      )
    ()
  }

  private def removePackageStatusFromQueue
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate
  ): Unit = {
    client
      .delete()
      .forPath(
        statusPath(packageCoordinate)
      )
    ()
  }

  private def createParentPath
  (
    client: CuratorFramework
  ): Unit = {
    client
      .create()
      .creatingParentsIfNeeded()
      .forPath(
        installQueuePath
      )
    ()
  }

  private def checkStatusDoesNotExist
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate
  ): Assertion = {
    val stat =
      Option(
        client
          .checkExists()
          .forPath(
            statusPath(packageCoordinate)
          )
      )
    assertResult(None)(stat)
  }

  private def pollForViewStatus
  (
    installQueue: InstallQueue,
    attempts: Int,
    size: Int
  ): Option[Map[PackageCoordinate, OperationStatus]] = {
    Stream.tabulate(attempts) { _ =>
      val oneSecond = 1000L
      Thread.sleep(oneSecond)
      Await.result(installQueue.viewStatus())
    }.dropWhile(_.size < size).headOption
  }

}

object InstallQueueSpec {
  private val coordinate1 =
    PackageCoordinate("coordinate", universe.v3.model.Version("1"))
  private val coordinate2 =
    PackageCoordinate("coordinate", universe.v3.model.Version("2"))
  private val coordinate3 =
    PackageCoordinate("coordinate", universe.v3.model.Version("3"))
  private val coordinate4 =
    PackageCoordinate("coordinate", universe.v3.model.Version("4"))
  private val coordinate5 =
    PackageCoordinate("coordinate", universe.v3.model.Version("5"))
  private val errorResponse1 =
    ErrorResponse("foo", "bar")
  private val errorResponse2 =
    ErrorResponse("abc", "def")
  private val notInQueueFailureMessageCoordinate1 =
    s"Attempted to signal failure on an " +
      s"operation not in the install queue: $coordinate1"
  private val alreadyFailedFailureMessageCoordinate1 =
    s"Attempted to signal failure on an " +
      s"operation that has failed: $coordinate1"
  private val notInQueueSuccessMessageCoordinate1 =
    s"Attempted to signal success on an " +
      s"operation not in the install queue: $coordinate1"
  private val alreadyFailedSuccessMessageCoordinate1 =
    s"Attempted to signal success on an " +
      s"operation that has failed: $coordinate1"
  private val universeInstall =
    UniverseInstall(TestingPackages.MaximalV3ModelV3PackageDefinition)
  private val universeInstallV4 =
    UniverseInstall(TestingPackages.MaximalV4ModelV4PackageDefinition)
}
