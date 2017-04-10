package com.mesosphere.cosmos.test

import com.mesosphere.cosmos.storage.installqueue.InstallQueue
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.ZooKeeperClient
import com.mesosphere.cosmos.zookeeper.Clients
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.util.Await
import com.twitter.util.Future
import org.apache.curator.framework.CuratorFramework
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

/** Mix this in to integration tests that need to access the install queue. */
trait InstallQueueFixture extends BeforeAndAfterAll { this: Suite =>

  import InstallQueueFixture._

  // Let's try to keep these private for now, and access them indirectly via methods on this mixin
  private[this] var zkClient: CuratorFramework = _
  private[this] var installQueue: InstallQueue = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    zkClient = Clients.createAndInitialize(ZooKeeperClient.uri)
    installQueue = InstallQueue(zkClient)(NullStatsReceiver)
  }

  override def afterAll(): Unit = {
    installQueue.close()
    zkClient.close()

    super.afterAll()
  }

  def awaitEmptyInstallQueue(): Unit = {
    Await.result(eventualFutureNone(installQueue.next))
  }

}

object InstallQueueFixture {

  def eventualFutureNone(future: () => Future[Option[_]]): Future[Unit] = {
    future().flatMap {
      case Some(_) => eventualFutureNone(future)
      case None => Future.Done
    }
  }

}
