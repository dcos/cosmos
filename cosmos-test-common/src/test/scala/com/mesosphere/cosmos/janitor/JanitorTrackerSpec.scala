package com.mesosphere.cosmos.janitor

import java.nio.file.Paths
import com.mesosphere.cosmos.janitor.SdkJanitor.UninstallClaimDenied
import com.mesosphere.cosmos.janitor.SdkJanitor.UninstallClaimGranted
import com.mesosphere.cosmos.janitor.SdkJanitor.UninstallFolder
import com.mesosphere.cosmos.storage.v1.model.Failed
import com.mesosphere.cosmos.storage.v1.model.InProgress
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.test.TestingCluster
import org.apache.zookeeper.KeeperException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.FreeSpec

final class JanitorTrackerSpec extends FreeSpec with BeforeAndAfterAll with BeforeAndAfterEach {
  val appId: AppId = AppId("/test")
  private[this] var zkCluster: TestingCluster = _
  private[this] var curator: CuratorFramework = _
  private[this] var lock: UninstallLock = _
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

    lock = new CuratorUninstallLock(curator)

    tracker = new JanitorTracker(curator, lock)
  }


  override protected def beforeEach(): Unit = {
    super.beforeEach()

    if (lock.isLockedByThisProcess(appId)) {
      lock.unlock(appId)
    }
  }

  override def afterAll(): Unit = {
    curator.close()
    zkCluster.close()

    super.afterAll()
  }

  "In the JanitorTracker" - {
    "In startUninstall" - {
      "If the lock is not owned, it is claimed and that status is marked as inprogress" in {
        assertResult(UninstallClaimGranted)(tracker.startUninstall(appId))
        assertResult(InProgress)(tracker.getStatus(appId))
      }
      "If the lock is already claimed, it cannot be claimed again by the same process" in {
        assertResult(UninstallClaimGranted)(tracker.startUninstall(appId))
        assertResult(UninstallClaimDenied)(tracker.startUninstall(appId))
      }
      "If the lock is already claimed, it cannot be claimed again across processes" in {
        assertResult(UninstallClaimGranted)(tracker.startUninstall(appId))

        val otherLock = new CuratorUninstallLock(curator)
        val otherTracker = new JanitorTracker(curator, otherLock)

        assertResult(UninstallClaimDenied)(otherTracker.startUninstall(appId))
      }
    }
    "In failUninstall" - {
      "The status is marked as failed, and the lock is released" in {
        assertResult(UninstallClaimGranted)(tracker.startUninstall(appId))
        assertResult(InProgress)(tracker.getStatus(appId))

        tracker.failUninstall(appId, List("1", "2", "3"))
        assertResult(Failed(List("1", "2", "3")))(tracker.getStatus(appId))
        assertResult(false)(lock.isLockedByThisProcess(appId))
      }
    }
    "In completeUninstall" - {
      "The lock is released and the entire zk record is deleted" in {
        assertResult(UninstallClaimGranted)(tracker.startUninstall(appId))
        assertResult(InProgress)(tracker.getStatus(appId))

        tracker.completeUninstall(appId)
        assertResult(false)(lock.isLockedByThisProcess(appId))
        assertThrows[KeeperException.NoNodeException](curator.getData.forPath(Paths.get(UninstallFolder, appId.toString).toString))
      }
    }
  }
}
