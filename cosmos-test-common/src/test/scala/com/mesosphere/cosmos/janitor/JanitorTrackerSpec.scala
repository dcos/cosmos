package com.mesosphere.cosmos.janitor

import com.mesosphere.cosmos.janitor.SdkJanitor.Failed
import com.mesosphere.cosmos.janitor.SdkJanitor.InProgress
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.test.TestingCluster
import org.apache.zookeeper.KeeperException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpec

class JanitorTrackerSpec extends FreeSpec with BeforeAndAfterAll {
  val appId: AppId = AppId("/test")
  private[this] var zkCluster: TestingCluster = _
  private[this] var curator: CuratorFramework = _
  private[this] var tracker: JanitorTracker = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    zkCluster = new TestingCluster(1)
    zkCluster.start()

    val base = 1000
    val retries = 1
    curator = CuratorFrameworkFactory
      .builder()
      .namespace("cosmos")
      .connectString(zkCluster.getConnectString)
      .retryPolicy(new ExponentialBackoffRetry(base, retries))
      .build()
    curator.start()

    tracker = new JanitorTracker(curator)
  }

  override def afterAll(): Unit = {
    curator.close()
    zkCluster.close()

    super.afterAll()
  }

  "In the JanitorTracker" - {
    "In createZkRecord" - {
      "A record is created when none exists" in {
        tracker.createZkRecord(appId)

        assertResult(InProgress)(tracker.getZkRecord(appId))
      }
      "The pre-existing record is overwritten if already present" in {
        tracker.createZkRecord(appId)
        tracker.failZkRecord(appId)
        assertResult(Failed)(tracker.getZkRecord(appId))

        tracker.createZkRecord(appId)
        assertResult(InProgress)(tracker.getZkRecord(appId))
      }
    }
    "In deleteZkRecord" - {
      "A record is correctly deleted" in {
        tracker.createZkRecord(appId)
        assertResult(InProgress)(tracker.getZkRecord(appId))

        tracker.deleteZkRecord(appId)
        assertThrows[KeeperException.NoNodeException](tracker.getZkRecord(appId))
      }
    }
    "In failZkRecord" - {
      "A record is marked as failed" in {
        tracker.createZkRecord(appId)
        assertResult(InProgress)(tracker.getZkRecord(appId))

        tracker.failZkRecord(appId)
        assertResult(Failed)(tracker.getZkRecord(appId))
      }
    }
    "In getZkRecord" - {
      "A record is properly deserialized from ZK" in {
        tracker.createZkRecord(appId)

        assertResult(InProgress)(tracker.getZkRecord(appId))
      }
      "A non-existent record throws an exception" in {
        assertThrows[KeeperException.NoNodeException](tracker.getZkRecord(AppId("/notthere")))
      }
    }
  }
}
