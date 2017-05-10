package com.mesosphere.cosmos

import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.mesosphere.cosmos.MarathonSdkJanitor._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.twitter.util.Await
import org.apache.http.HttpStatus
import org.slf4j.Logger

/** Janitor Daemon for removing SDK based services as they complete their uninstalls. */
final class MarathonSdkJanitor(adminRouter: AdminRouter) {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val queue = new DelayQueue[JanitorRequest]()
  val executor = Executors.newFixedThreadPool(1)

  def delete(appId: AppId, session: RequestSession): Unit = {
    // TODO: Add to ZK

    // Queue work.
    queue.add(JanitorRequest(appId, session, 0, System.currentTimeMillis(), 0))
    // Handle false to make style checker happy.
    logger.info("Successfully added delete request to queue for {}", appId)
  }

  /** Runnable that knows how to wait and then clean up. */
  class Janitor() extends Runnable {

    override def run(): Unit = {
      logger.info("Starting work loop...")
      while (true) {
        try {
          logger.debug("Queuing for work.")
          // Wait for work.
          val request = queue.take()
          logger.debug("Took request to delete {}", request.appId)

          logger.info("{} was last checked at {}. Rechecking now...",
            request.appId,
            request.lastAttempt.toString)

          val futureAppResponse = adminRouter.getApp(request.appId)(session = request.session)
          val app = Await.result(futureAppResponse).app

          if (checkUninstall(app, request.created)) delete(request) else requeue(request)
        } catch {
          case ie: InterruptedException =>
            logger.error("Janitor request queue was interrupted", ie)
          case e: Exception =>
            logger.error("Janitor encountered an exception during execution", e)
        }
      }
    }

    /** Check the status of the app's uninstall. Return false if uninstall is still in progress. */
    def checkUninstall(app: MarathonApp, created: Long): Boolean = {
      logger.info("Checking the status of the uninstall for app: {}", app.id)
      // TODO: Actually check :)

      // For now, just naively wait for 2 minutes.
      if (System.currentTimeMillis() > created + 120000) true else false
    }

    def delete(request: JanitorRequest): Unit = {
      // App is ready to be deleted. Do so.
      logger.info("{} is ready to be deleted. Telling Marathon to delete it.",
        request.appId)
      val deleteResult = Await.result(adminRouter.deleteApp(request.appId)(session = request.session))
      deleteResult.statusCode match {
        case badRequest if 400 until 499 contains badRequest =>
          logger.info("Encountered Marathon error: {} when deleting {}. Failing.", badRequest,
            request.appId)
          fail(request)
          ()
        case retryRequest if 500 until 599 contains retryRequest =>
          logger.info("Encountered Marathon error: {} when deleting {}. Retrying.", retryRequest,
            request.appId)
          requeue(request.copy(failures = request.failures + 1))
          ()
        case HttpStatus.SC_OK =>
          // TODO: Remove from ZK
          logger.info("Deleted app: {}", request.appId)
          ()
      }
    }

      def fail(request: JanitorRequest): Unit = {
        // TODO: Remove from ZK
        logger.error("Failed to delete app: {} after {} attempts.", request.appId, request.failures)
      }

      /** Determine if the request should be requeued. If it should, do so. */
      def requeue(request: JanitorRequest): Unit = {
        logger.info("Evaluating if request to delete {} should be added back to the queue.", request.appId)
        if (request.failures >= MaximumFailures) {
          logger.info("The request to delete {} has failed {} times, exceeding the limit. Failing the request.",
            request.appId, MaximumFailures)
          fail(request)
        } else {
          logger.info("The request to delete {} has not exceeded the failure limit. Adding it back to the queue.",
            request.appId)
          queue.add(request.copy(lastAttempt = System.currentTimeMillis()))
          ()
        }
      }
    }

  def start(): Unit = {
    logger.info("Starting the janitor thread...")
    executor.submit(new Janitor())
    ()
  }

  def stop(): Unit = {
    logger.info("Stopping the janitor thread...")
    executor.shutdown()
  }
}

object MarathonSdkJanitor {
  val MaximumFailures: Int = 5
  val TimeBetweenChecksMilliseconds: Int = 10000

  /** Encapsulate the meat of a request. */
  case class JanitorRequest(appId: AppId,
                            session: RequestSession,
                            failures: Int,
                            created: Long,
                            lastAttempt: Long) extends Delayed {

    override def compareTo(o: Delayed): Int = {
      val ownDelay = getDelay(TimeUnit.MILLISECONDS)
      val otherDelay = o.getDelay(TimeUnit.MILLISECONDS)

      if (ownDelay < otherDelay) {
        -1
      } else if (ownDelay == otherDelay) {
        0
      } else {
        1
      }
    }

    override def getDelay(unit: TimeUnit): Long = {
      unit.convert(TimeBetweenChecksMilliseconds + lastAttempt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    }
  }
}
