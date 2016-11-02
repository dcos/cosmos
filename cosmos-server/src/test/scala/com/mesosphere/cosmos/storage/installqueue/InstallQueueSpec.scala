package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.InstallQueueError
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.storage.Envelope
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.model.PackageDefinition
import com.twitter.util.Await
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.KeeperException
import org.scalatest.Outcome
import org.scalatest.fixture

class InstallQueueSpec extends fixture.FreeSpec {

  import InstallQueueSpec._
  import InstallQueue._

  case class FixtureParam(client: CuratorFramework, installQueue: InstallQueue)

  override def withFixture(test: OneArgTest): Outcome = {
    val path = "/"
    val retries = 10
    val baseSleepTime = 1000
    val client = CuratorFrameworkFactory.newClient(
      path,
      new ExponentialBackoffRetry(baseSleepTime, retries)
    )
    client.start()
    client.getZookeeperClient.blockUntilConnectedOrTimedOut()

    try {
      withFixture(test.toNoArgTest(FixtureParam(client, InstallQueue(client))))
    } finally {
      try {
        client.delete().deletingChildrenIfNeeded().forPath(installQueuePath)
        ()
      } catch {
        case e: KeeperException.NoNodeException =>
      }
    }
  }

  private def checkInstallQueueContents
  (
    f: FixtureParam,
    packageCoordinate: PackageCoordinate,
    expected: OperationStatus
  ): Unit = {
    val data =
      f.client
        .getData
        .forPath(
          statusPath(coordinate1)
        )
    val operationStatus = Envelope.decodeData[OperationStatus](data)
    assertResult(expected)(operationStatus)
  }

  private def insertPackageStatusIntoQueue
  (
    f: FixtureParam,
    packageCoordinate: PackageCoordinate,
    contents: OperationStatus
  ): Unit = {
    f.client
      .create()
      .creatingParentsIfNeeded()
      .forPath(
        statusPath(packageCoordinate),
        Envelope.encodeData(contents)
      )
    ()
  }

  private def removePackageStatusFromQueue
  (
    f: FixtureParam,
    packageCoordinate: PackageCoordinate
  ): Unit = {
    f.client
      .delete()
      .forPath(
        statusPath(packageCoordinate)
      )
    ()
  }

  private def createParentPath
  (
    f: FixtureParam
  ): Unit = {
    f.client
      .create()
      .creatingParentsIfNeeded()
      .forPath(
        installQueuePath
      )
    ()
  }

  private def checkStatusDoesNotExist
  (
    f: FixtureParam,
    packageCoordinate: PackageCoordinate
  ): Unit = {
    val stat =
      Option(
        f.client
          .checkExists()
          .forPath(
            statusPath(packageCoordinate)
          )
      )
    assertResult(None)(stat)
  }

  private def pollForViewStatus
  (
    f: FixtureParam,
    attempts: Int,
    size: Int
  ): Option[Map[PackageCoordinate, OperationStatus]] = {
    Stream.tabulate(attempts) { _ =>
      val oneSecond = 1000L
      Thread.sleep(oneSecond)
      Await.result(f.installQueue.viewStatus())
    }.dropWhile(_.size < size).headOption
  }

  "Producer View" - {
    "Add an operation " - {
      "when no parent path exists" ignore { f =>
        val addResult = Await.result(
          f.installQueue.add(coordinate1, operation1)
        )
        assertResult(Created)(addResult)

        checkInstallQueueContents(f, coordinate1, statusWithOnlyOperation1)
      }

      "when the parent path exists but the status does not" ignore { f =>
        createParentPath(f)
        val addResult = Await.result(
          f.installQueue.add(coordinate1, operation1)
        )
        assertResult(Created)(addResult)

        checkInstallQueueContents(f, coordinate1, statusWithOnlyOperation1)
      }

      "on a coordinate that has a pending operation but no failures" ignore { f =>
        insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)

        val addResult = Await.result(
          f.installQueue.add(coordinate1, operation1)
        )
        assertResult(AlreadyExists)(addResult)

        checkInstallQueueContents(f, coordinate1, statusWithOnlyOperation1)
      }

      "on a coordinate that has a failed operation, but no pending operation" ignore { f =>
        insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyFailure1)

        val addResult = Await.result(
          f.installQueue.add(coordinate1, operation1)
        )
        assertResult(Created)(addResult)

        checkInstallQueueContents(f, coordinate1, statusWithOperation1AndFailure1)
      }

      "on a coordinate that has an operation and a failure" ignore { f =>
        insertPackageStatusIntoQueue(f, coordinate1, statusWithOperation1AndFailure1)

        val addResult = Await.result(
          f.installQueue.add(coordinate1, operation1)
        )
        assertResult(AlreadyExists)(addResult)

        checkInstallQueueContents(f, coordinate1, statusWithOperation1AndFailure1)
      }
    }

    "Add multiple non-conflicting operations" ignore { f =>
      val addTwoOperations =
        for {
          add1 <- f.installQueue.add(coordinate1, operation1)
          add2 <- f.installQueue.add(coordinate2, operation1)
        } yield (add1, add2)

      val (add1, add2) = Await.result(addTwoOperations)
      assertResult(Created)(add1)
      assertResult(Created)(add2)

      checkInstallQueueContents(f, coordinate1, statusWithOnlyOperation1)
      checkInstallQueueContents(f, coordinate2, statusWithOnlyOperation1)
    }
  }

  "Processor view" - {
    "failure" - {
      "Fail an operation " - {
        "when no parent path exists" ignore { f =>
          val error = intercept[InstallQueueError](
            Await.result(
              f.installQueue.failure(coordinate1, errorResponse1)
            )
          )
          assertResult(notInQueueFailureMessageCoordinate1)(error.msg)
        }

        "when the parent path exists but the status does not" ignore { f =>
          createParentPath(f)
          val error = intercept[InstallQueueError](
            Await.result(
              f.installQueue.failure(coordinate1, errorResponse1)
            )
          )
          assertResult(notInQueueFailureMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has a pending operation but no failures" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)
          Await.result(
            f.installQueue.failure(coordinate1, errorResponse1)
          )
          checkInstallQueueContents(f, coordinate1, statusWithOnlyFailure1)
        }

        "on a coordinate that has a failed operation, but no pending operation" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyFailure1)
          val error = intercept[InstallQueueError](
            Await.result(
              f.installQueue.failure(coordinate1, errorResponse1)
            )
          )
          assertResult(alreadyFailedFailureMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has an operation and a failure" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOperation1AndFailure1)
          Await.result(
            f.installQueue.failure(coordinate1, errorResponse1)
          )
          checkInstallQueueContents(f, coordinate1, statusWithOnlyFailure1)
        }
      }

      "Fail multiple non-conflicting operations" ignore { f =>
        insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)
        insertPackageStatusIntoQueue(f, coordinate2, statusWithOnlyOperation1)
        val failTwoOperations =
          for {
            _ <- f.installQueue.failure(coordinate1, errorResponse1)
            _ <- f.installQueue.failure(coordinate2, errorResponse1)
          } yield ()
        Await.result(failTwoOperations)
        checkInstallQueueContents(f, coordinate1, statusWithOnlyFailure1)
        checkInstallQueueContents(f, coordinate2, statusWithOnlyFailure1)
      }
    }

    "success" - {
      "Success on an operation " - {
        "when no parent path exists" ignore { f =>
          val error = intercept[InstallQueueError](
            Await.result(
              f.installQueue.success(coordinate1)
            )
          )
          assertResult(notInQueueSuccessMessageCoordinate1)(error.msg)
        }

        "when the parent path exists but the status does not" ignore { f =>
          createParentPath(f)
          val error = intercept[InstallQueueError](
            Await.result(
              f.installQueue.success(coordinate1)
            )
          )
          assertResult(notInQueueSuccessMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has a pending operation but no failures" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)
          Await.result(
            f.installQueue.success(coordinate1)
          )
          checkStatusDoesNotExist(f, coordinate1)
        }

        "on a coordinate that has a failed operation, but no pending operation" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyFailure1)
          val error = intercept[InstallQueueError](
            Await.result(
              f.installQueue.success(coordinate1)
            )
          )
          assertResult(alreadyFailedSuccessMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has an operation and a failure" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOperation1AndFailure1)
          Await.result(
            f.installQueue.success(coordinate1)
          )
          checkStatusDoesNotExist(f, coordinate1)
        }
      }

      "Success on multiple non-conflicting operations" ignore { f =>
        insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)
        insertPackageStatusIntoQueue(f, coordinate2, statusWithOnlyOperation1)
        val successOnTwoOperations =
          for {
            _ <- f.installQueue.success(coordinate1)
            _ <- f.installQueue.success(coordinate2)
          } yield ()
        Await.result(successOnTwoOperations)
        checkStatusDoesNotExist(f, coordinate1)
        checkStatusDoesNotExist(f, coordinate2)
      }
    }

    "next" - {
      "Next when " - {
        "no parent path exists" ignore { f =>
          val nextPendingOperation = Await.result(
            f.installQueue.next()
          )
          assertResult(None)(nextPendingOperation)
        }

        "the parent path exists but there are no pending operations" ignore { f =>
          createParentPath(f)
          val nextPendingOperation = Await.result(
            f.installQueue.next()
          )
          assertResult(None)(nextPendingOperation)
        }

        "there is a coordinate that has a pending operation but no failures" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)
          val nextPendingOperation = Await.result(
            f.installQueue.next()
          )
          assertResult(Some(pendingOperation1Coordinate1NoFailure))(nextPendingOperation)
        }

        "there is a coordinate that has a failed operation, but no pending operation" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyFailure1)
          val nextPendingOperation = Await.result(
            f.installQueue.next()
          )
          assertResult(None)(nextPendingOperation)
        }

        "there is a coordinate that has an operation and a failure" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOperation1AndFailure1)
          val nextPendingOperation = Await.result(
            f.installQueue.next()
          )
          assertResult(Some(pendingOperation1Coordinate1Failure1))(nextPendingOperation)
        }

        "there are multiple pending operations some of which have failed" ignore { f =>
          insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)
          insertPackageStatusIntoQueue(f, coordinate2, statusWithOnlyOperation1)
          insertPackageStatusIntoQueue(f, coordinate3, statusWithOnlyFailure1)
          insertPackageStatusIntoQueue(f, coordinate4, statusWithOnlyFailure1)
          insertPackageStatusIntoQueue(f, coordinate5, statusWithOperation1AndFailure1)

          val n1 = Await.result(f.installQueue.next())
          removePackageStatusFromQueue(f, coordinate1)

          val n2 = Await.result(f.installQueue.next())
          removePackageStatusFromQueue(f, coordinate2)

          val n3 = Await.result(f.installQueue.next())
          removePackageStatusFromQueue(f, coordinate5)

          val n4 = Await.result(f.installQueue.next())
          val n5 = Await.result(f.installQueue.next())

          assertResult(Some(pendingOperation1Coordinate1NoFailure))(n1)
          assertResult(Some(pendingOperation1Coordinate2NoFailure))(n2)
          assertResult(Some(pendingOperation1Coordinate5Failure1))(n3)
          assertResult(None)(n4)
          assertResult(None)(n5)
        }
      }
    }

  }

  "Reader view" - {
    "ViewStatus should " +
      "return all pending and failed operations in the queue" ignore { f =>
      insertPackageStatusIntoQueue(f, coordinate1, statusWithOnlyOperation1)
      insertPackageStatusIntoQueue(f, coordinate2, statusWithOnlyOperation1)
      insertPackageStatusIntoQueue(f, coordinate3, statusWithOnlyFailure1)
      insertPackageStatusIntoQueue(f, coordinate4, statusWithOnlyFailure1)
      insertPackageStatusIntoQueue(f, coordinate5, statusWithOperation1AndFailure1)

      val pollCount = 10
      val expectedSize = 5
      val actualState = pollForViewStatus(f, pollCount, expectedSize)
      val expectedState =
        Some(Map(
          coordinate1 -> statusWithOnlyOperation1,
          coordinate2 -> statusWithOnlyOperation1,
          coordinate3 -> statusWithOnlyFailure1,
          coordinate4 -> statusWithOnlyFailure1,
          coordinate5 -> statusWithOperation1AndFailure1
        ))
      assertResult(expectedState)(actualState)
    }
  }

}

object InstallQueueSpec {
  private val coordinate1 =
    PackageCoordinate("foo", PackageDefinition.Version("3.2.1"))
  private val operation1 =
    UniverseInstall(TestingPackages.MinimalV3ModelV3PackageDefinition)
  private val coordinate2 =
    PackageCoordinate("foo", PackageDefinition.Version("3.2.3"))
  private val coordinate3 =
    PackageCoordinate("bar", PackageDefinition.Version("3.2.3"))
  private val coordinate4 =
    PackageCoordinate("baz", PackageDefinition.Version("3.2.3"))
  private val coordinate5 =
    PackageCoordinate("link", PackageDefinition.Version("3.2.3"))
  private val errorResponse1 =
    ErrorResponse("foo", "bar")
  private val failure1 =
    OperationFailure(operation1, errorResponse1)
  private val statusWithOnlyOperation1 =
    Pending(operation1, None)
  private val statusWithOperation1AndFailure1 =
    Pending(operation1, Some(failure1))
  private val statusWithOnlyFailure1 =
    Failed(failure1)
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
  private val pendingOperation1Coordinate1NoFailure =
    PendingOperation(coordinate1, operation1, None)
  private val pendingOperation1Coordinate2NoFailure =
    PendingOperation(coordinate2, operation1, None)
  private val pendingOperation1Coordinate1Failure1 =
    PendingOperation(coordinate1, operation1, Some(failure1))
  private val pendingOperation1Coordinate5Failure1 =
    PendingOperation(coordinate5, operation1, Some(failure1))
}
