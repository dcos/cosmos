package com.mesosphere.cosmos.janitor

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.test.TestingCluster
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.FreeSpec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class UninstallLockSpec extends FreeSpec with BeforeAndAfterAll with BeforeAndAfterEach {
  val appId: AppId = AppId("/test")
  private[this] var zkCluster: TestingCluster = _
  private[this] var curator: CuratorFramework = _
  private[this] var lock: UninstallLock = _

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
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    lock = new CuratorUninstallLock(curator)
  }

  "In the uninstall lock" - {
    "The lock can be acquired and released" in {
      assertResult(true)(lock.lock(appId))
      assertResult(true)(lock.isLockedByThisProcess(appId))
      lock.unlock(appId)
      assertResult(false)(lock.isLockedByThisProcess(appId))
    }
    "Multiple threads can share the lock" in {
      lock.lock(appId)

      val unlockFuture: Future[Unit] = Future {
        lock.unlock(appId)
      }

      Await.result(unlockFuture, Duration.Inf)
    }
    "If the lock is already held, it cannot be acquired by anyone else" in {
      assertResult(true)(lock.lock(appId))

      // Represent another Cosmos by creating a second UninstallLock
      val lockCopy = new CuratorUninstallLock(curator)
      val lockFuture: Future[Boolean] = Future[Boolean] {
        lockCopy.lock(appId)
      }

      assertResult(false)(Await.result(lockFuture, Duration.Inf))
      lock.unlock(appId)
    }
    "isOwnedByThisProcess returns false if the lock isn't acquired" in {
      assertResult(false)(lock.isLockedByThisProcess(appId))
    }
  }

  override def afterAll(): Unit = {
    curator.close()
    zkCluster.close()

    super.afterAll()
  }

}
