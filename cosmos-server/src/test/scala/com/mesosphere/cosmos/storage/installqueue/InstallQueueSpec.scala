package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.storage.Envelope
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.model.PackageDefinition
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Await
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.scalatest.FreeSpec
import scala.collection.JavaConversions._
import scala.util.Try

class InstallQueueSpec extends FreeSpec {
  import InstallQueueSpec._

  val path = "/"
  val pendingPath = "/package/pendingOperations"
  val failedPath = "/package/failedOperations"

  private[this] def startClient(): CuratorFramework = {
    val retries = 10
    val baseSleepTime = 1000
    val client = CuratorFrameworkFactory.newClient(
      path,
      new ExponentialBackoffRetry(baseSleepTime, retries)
    )
    client.start()
    client.getZookeeperClient.blockUntilConnectedOrTimedOut()
    client
  }

  private[this] def cleanUpZk(client: CuratorFramework): Unit = {
    Try(client.delete().deletingChildrenIfNeeded().forPath("/package"))
    ()
  }

  "Producer view" - {
    "Adding a new operation should succeed" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      val actualResult = Await.result(iq.add(pc, op))
      val expectedResult = Created
      assertResult(expectedResult)(actualResult)

      val encodedPc = client.getChildren.forPath(pendingPath).toList.head
      val actualPc = encodedPc.as[Try[PackageCoordinate]].toOption.get
      assertResult(pc)(actualPc)

      val encodedOperation = client.getData.forPath(s"$pendingPath/${pc.as[String]}")
      val actualOperation = Envelope.decodeData[Operation](encodedOperation)
      assertResult(op)(actualOperation)

      cleanUpZk(client)
    }

    "Adding a duplicate operations should fail" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      val firstResult = Await.result(iq.add(pc, op))
      val firstExpected = Created
      assertResult(firstExpected)(firstResult)

      val secondResult = Await.result(iq.add(pc, op))
      val secondExpected = AlreadyExists
      assertResult(secondExpected)(secondResult)

      cleanUpZk(client)
    }
  }

  "Reader view" - {
    "should provide a view of the state of the install queue" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      Await.result(iq.add(pc, op))

      def poll(attempt: Int): Option[Map[PackageCoordinate, OperationStatus]] = {
        attempt match {
          case 0 => None
          case _ =>
            val state = Await.result(iq.viewStatus())
            if (state.nonEmpty) {
              Some(state)
            } else {
              poll(attempt - 1)
            }
        }
      }

      val pollCount = 10
      val state = poll(pollCount).get
      val actualPc = state.keys.head
      assertResult(pc)(actualPc)

      val expectedStatus = OperationStatus(Some(op), None)
      val actualStatus = state(actualPc)
      assertResult(expectedStatus)(actualStatus)

      cleanUpZk(client)
    }
  }

  "Processor view" - {
    "Ensure we get the None for next when there are no operations" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      val actualNext = Await.result(iq.next())
      val expectedNext = None
      assertResult(expectedNext)(actualNext)

      cleanUpZk(client)
    }

    "Ensure we get the correct next when there are multiple operations" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      val actualNext = Await.result(
        for {
          addResult <- iq.add(pc, op)
          next <- iq.next()
        } yield next
      ).get
      val expectedNext = PendingOperation(pc, op, None, actualNext.creationTime)
      assertResult(expectedNext)(actualNext)

      cleanUpZk(client)
    }

    "Ensure we can indicate that a package was successfully installed" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      val actualNext = Await.result(
        for {
          addResult <- iq.add(pc, op)
          next <- iq.next()
          _ <- iq.success(next.get.packageCoordinate)
        } yield next
      ).get
      val expectedNext = PendingOperation(pc, op, None, actualNext.creationTime)
      assertResult(expectedNext)(actualNext)

      // assert there are no further operations pending
      assertResult(None)(Await.result(iq.next()))

      val pending = client.getChildren.forPath(pendingPath)
      assert(pending.isEmpty)

      cleanUpZk(client)
    }

    "Ensure ordering of operations" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      val (result1, result2) =
        Await.result(
          for {
            r1 <- iq.add(pc, op)
            r2 <- iq.add(pc2, op)
          } yield (r1, r2)
        )

      assertResult(Created)(result1)
      assertResult(Created)(result2)

      val actualNext = Await.result(iq.next()).get
      val expectedNext = PendingOperation(pc, op, None, actualNext.creationTime)
      assertResult(expectedNext)(actualNext)

      cleanUpZk(client)
    }

    "Ensure we can fail an operation" ignore {
      val client = startClient()
      val iq = InstallQueue(client)

      val actualNext = Await.result(
        for {
          addResult <- iq.add(pc, op)
          nextOpt <- iq.next()
          next = nextOpt.get
          _ <- iq.failure(next.packageCoordinate, next.operation, ErrorResponse("error", "this is an error"))
        } yield next
      )
      val expectedNext = PendingOperation(pc, op, None, actualNext.creationTime)
      assertResult(expectedNext)(actualNext)

      // assert there are no further operations pending
      assertResult(None)(Await.result(iq.next()))

      val pending = client.getChildren.forPath(pendingPath)
      assert(pending.isEmpty)

      val failed = client.getChildren.forPath(failedPath)
      assert(failed.nonEmpty)

      cleanUpZk(client)
    }
  }

}

object InstallQueueSpec {
  val pc = PackageCoordinate("foo", PackageDefinition.Version("3.2.1"))
  val op = UniverseInstall(TestingPackages.MinimalV3ModelV3PackageDefinition)
  val pc2 = PackageCoordinate("foo", PackageDefinition.Version("3.2.3"))
}
