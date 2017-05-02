package com.mesosphere.cosmos
import java.time.Duration
import java.util.concurrent.{BlockingQueue, Executors, LinkedBlockingQueue}

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonApp}
import com.twitter.util.Await
import org.apache.http.HttpStatus
import org.slf4j.Logger

/** A [[com.mesosphere.cosmos.SDKJanitor]] implementation for Marathon. */
class MarathonSDKJanitor(adminRouter: AdminRouter) extends SDKJanitor {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val MAXIMUM_FAILURES = 5
  val TIME_BETWEEN_CHECKS_MILLISECONDS = 10000
  val queue = new LinkedBlockingQueue[JanitorRequest]()
  val executor = Executors.newFixedThreadPool(1)

  override def delete(appId: AppId, session: RequestSession): Boolean = {
    // TODO: Add to ZK

    // Queue work.
    queue.add(new JanitorRequest(appId, session, 0, 0))
  }

  /** Encapsulate the meat of a request. */
  class JanitorRequest(val appId: AppId,
                       val session: RequestSession,
                       val failures: Int,
                       val lastAttempt: Long)

  /** Runnable that knows how to wait and then clean up. */
  class Janitor(queue: BlockingQueue[JanitorRequest]) extends Runnable {

    override def run(): Unit = {
      logger.info("Starting work loop...")
      while (true) {
        try {
          logger.info("Queuing for work.")
          // Wait for work.
          val request = queue.take()
          logger.info("Took request to delete {}", request.appId)

          // Is this work ready to be retested?
          if (System.currentTimeMillis() - request.lastAttempt < TIME_BETWEEN_CHECKS_MILLISECONDS) {
            logger.info("{} was last checked at {}. Adding back to queue and waiting.",
              request.appId,
              request.lastAttempt.toString)
            queue.add(request)
          } else {
            logger.info("{} was last checked at {}. Rechecking now...",
              request.appId,
              request.lastAttempt.toString)
            // Yes, there is work to do.
            // Retrieve the app definition from Marathon.
            val futureAppResponse = adminRouter.getApp(request.appId)(session = request.session)
            val app = Await.result(futureAppResponse).app

            if (checkUninstall(app)) delete(request) else requeue(request)
          }
        } catch {
          case ie: InterruptedException =>
            logger.error("Janitor request queue was interrupted", ie)
          case e: Exception =>
            logger.error("Janitor encountered an exception during execution", e)
        }
      }
    }

    /** Check the status of the app's uninstall. Return false if uninstall is still in progress. */
    def checkUninstall(app: MarathonApp): Boolean = {
      logger.info("Checking the status of the uninstall for app: {}", app.id)
      // TODO: Actually check :)
      false
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
          requeue(request)
          ()
        case HttpStatus.SC_ACCEPTED =>
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
      def requeue(request: JanitorRequest): Boolean = {
        logger.info("Evaluating if request to delete {} should be added back to the queue.", request.appId)
        if (request.failures + 1 >= MAXIMUM_FAILURES) {
          logger.info("The request to delete {} has failed {} times, exceeding the limit. Failing the request.",
            request.appId, request.failures)
          fail(request)
          false
        } else {
          logger.info("The request to delete {} has not exceeded the limit. Adding it back to the queue.",
            request.appId)
          queue.add(new JanitorRequest(request.appId,
            request.session,
            request.failures + 1,
            System.currentTimeMillis()))
        }
      }
    }

  override def start(): Unit = {
    logger.info("Starting the janitor thread...")
    executor.submit(new Janitor(queue))
    ()
  }

  override def stop(): Unit = {
    logger.info("Stopping the janitor thread...")
    executor.shutdown()
  }
}
