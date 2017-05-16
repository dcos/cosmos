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

/** Janitor Daemon for removing SDK based services as they complete their uninstalls. */
final class SdkJanitor(
  tracker: Tracker,
  worker: Worker,
  queue: DelayQueue[Request],
  checkInterval: Int
) {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private val executor: ExecutorService = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder()
    .setDaemon(true)
    .setNameFormat("sdk-janitor-thread-%d")
    .build())

  def delete(appId: AppId, session: RequestSession): Unit = {
    queue.add(JanitorRequest(appId, session, 0, System.currentTimeMillis(), checkInterval, 0))
    tracker.createZkRecord(appId)
    logger.info("Successfully added delete request to queue for {}", appId)
  }

  def start(): Unit = {
    logger.info("Starting the janitor...")
    executor.submit(worker)
    ()
  }

  def stop(timeout: Long = 0, timeUnit: TimeUnit = TimeUnit.SECONDS): Unit = {
    logger.info("Stopping the janitor...")
    worker.stop()
    executor.shutdown()
    val _ = if (timeout > 0) executor.awaitTermination(timeout, timeUnit)
  }
}

object SdkJanitor {
  val MaximumFailures: Int = 5
  val DefaultTimeBetweenChecksMilliseconds: Int = 10000
  val SdkApiVersionLabel = "DCOS_COMMONS_API_VERSION"
  val UninstallFolder = "/uninstalls"

  def initializeJanitor(curator: CuratorFramework, adminRouter: AdminRouter): SdkJanitor = {
    val queue = new DelayQueue[Request]()
    val tracker = new JanitorTracker(curator)
    val worker = new JanitorWorker(queue, tracker, adminRouter)
    new SdkJanitor(tracker, worker, queue, DefaultTimeBetweenChecksMilliseconds)
  }

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
                            failures: Int,
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
