package com.mesosphere.cosmos.janitor

import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.janitor.SdkJanitor._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.slf4j.Logger

trait Janitor {
  def claimUninstall(appId: AppId): UninstallClaim
  def releaseUninstall(appId: AppId): Unit
  def delete(appId: AppId, session: RequestSession): Unit
  def start(): Unit
  def stop(timeout: Long = 0, timeUnit: TimeUnit = TimeUnit.SECONDS): Unit
}

/** Janitor Daemon for removing SDK based services as they complete their uninstalls. */
final class SdkJanitor(
  tracker: Tracker,
  worker: Worker,
  queue: DelayQueue[Request],
  lock: UninstallLock,
  checkInterval: Int
) extends Janitor {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private val executor: ExecutorService = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder()
    .setDaemon(true)
    .setNameFormat("sdk-janitor-thread-%d")
    .build())

  override def claimUninstall(appId: AppId): UninstallClaim = tracker.startUninstall(appId)

  override def releaseUninstall(appId: AppId): Unit = tracker.completeUninstall(appId)

  override def delete(appId: AppId, session: RequestSession): Unit = {
    queue.add(JanitorRequest(appId, session, List(), System.currentTimeMillis(), checkInterval, 0))
    logger.info("Successfully added delete request to queue for {}", appId)
  }

  override def start(): Unit = {
    logger.info("Starting the janitor...")
    val _ = executor.submit(worker)
    logger.info("Janitor started.")
  }

  override def stop(timeout: Long = 0, timeUnit: TimeUnit = TimeUnit.SECONDS): Unit = {
    logger.info("Stopping the janitor...")
    worker.stop()
    executor.shutdown()
    val _ = if (timeout > 0) executor.awaitTermination(timeout, timeUnit)
    logger.info("Janitor stopped.")
  }
}

object SdkJanitor {
  val MaximumFailures: Int = 5
  val DefaultTimeBetweenChecksMilliseconds: Int = 10000
  val SdkApiVersionLabel = "DCOS_COMMONS_API_VERSION"
  val UninstallFolder = "/uninstalls"

  def initializeJanitor(curator: CuratorFramework, adminRouter: AdminRouter): Janitor = {
    val queue = new DelayQueue[Request]()
    val lock = new CuratorUninstallLock(curator)
    val tracker = new JanitorTracker(curator, lock)
    val worker = new JanitorWorker(queue, tracker, adminRouter)

    new SdkJanitor(tracker, worker, queue, lock, DefaultTimeBetweenChecksMilliseconds)
  }

  sealed trait UninstallClaim
  case object UninstallClaimed extends UninstallClaim
  case object UninstallAlreadyClaimed extends UninstallClaim

  sealed trait Request extends Delayed
  case class ShutdownRequest() extends Request {
    override def getDelay(unit: TimeUnit): Long = {
      0L
    }

    override def compareTo(o: Delayed): Int = {
      getDelay(TimeUnit.MILLISECONDS).compare(o.getDelay(TimeUnit.MILLISECONDS))
    }
  }
  case class JanitorRequest(appId: AppId,
                            session: RequestSession,
                            failures: List[String],
                            created: Long,
                            checkInterval: Int,
                            lastAttempt: Long) extends Request {

    override def compareTo(o: Delayed): Int = {
      getDelay(TimeUnit.MILLISECONDS).compare(o.getDelay(TimeUnit.MILLISECONDS))
    }

    override def getDelay(unit: TimeUnit): Long = {
      unit.convert(checkInterval + lastAttempt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    }
  }
}


// User runs uninstall
// UninstallHandler checks if the uninstall has already been started (trys to acquire the lock)
