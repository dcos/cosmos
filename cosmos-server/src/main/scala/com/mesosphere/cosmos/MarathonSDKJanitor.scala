package com.mesosphere.cosmos

import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.mesosphere.cosmos.MarathonSdkJanitor._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.twitter.finagle.http._
import com.twitter.util.Await
import org.apache.http.HttpStatus
import org.slf4j.Logger

/** Janitor Daemon for removing SDK based services as they complete their uninstalls. */
final class MarathonSdkJanitor(adminRouter: AdminRouter) {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val queue = new DelayQueue[JanitorRequest]()
  val executor = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder()
    .setDaemon(true)
    .setNameFormat("sdk-janitor-thread-%d")
    .build())
  @volatile var running = true

  def delete(appId: AppId, session: RequestSession): Unit = {
    // TODO: Add to ZK

    queue.add(JanitorRequest(appId, session, 0, System.currentTimeMillis(), 0))
    logger.info("Successfully added delete request to queue for {}", appId)
  }

  /** Runnable that knows how to wait and then clean up. */
  final class Janitor() extends Runnable {

    override def run(): Unit = {
      logger.info("Starting work loop...")
      while (running) {
        try {
          logger.debug("Queuing for work.")
          val request = queue.take()
          logger.debug("Took request to delete {}", request.appId)

          logger.info("{} was last checked at {}. Rechecking now...",
            request.appId,
            request.lastAttempt.toString)

          val futureAppResponse = adminRouter.getApp(request.appId)(session = request.session)
          val app = Await.result(futureAppResponse).app

          if (checkUninstall(app, request.created)(request.session)) delete(request) else requeue(request)
        } catch {
          case ie: InterruptedException =>
            logger.error("Janitor request queue was interrupted", ie)
          case e: Exception =>
            logger.error("Janitor encountered an exception during execution", e)
        }
      }
    }

    /** Check the status of the app's uninstall. Return false if uninstall is still in progress. */
    def checkUninstall(app: MarathonApp, created: Long)(implicit session: RequestSession): Boolean = {
      logger.info("Checking the status of the uninstall for app: {}", app.id)

      Await.result(adminRouter.getSdkServicePlanStatus(
        service = app.id.toString,
        apiVersion = app.labels.get(SdkApiVersionLabel).getOrElse("v1"),
        plan = "deploy"
      )(session = session)).status match {
        case Status.Ok => true
        case _ => false
      }
    }

    def delete(request: JanitorRequest): Unit = {
      logger.info("{} is ready to be deleted. Telling Marathon to delete it.",
        request.appId)
      try {
        val deleteResult = Await.result(adminRouter.deleteApp(request.appId)(session = request.session))
        deleteResult.statusCode match {
          case badRequest if 400 until 499 contains badRequest =>
            logger.error("Encountered Marathon error: {} when deleting {}. Failing.", badRequest,
              request.appId)
            fail(request)
            ()
          case retryRequest if 500 until 599 contains retryRequest =>
            logger.error("Encountered Marathon error: {} when deleting {}. Retrying.", retryRequest,
              request.appId)
            requeue(request.copy(failures = request.failures + 1))
            ()
          case HttpStatus.SC_OK =>
            // TODO: Remove from ZK
            logger.info("Deleted app: {}", request.appId)
            ()
          case default =>
            logger.error("Encountered unexpected status: {} when deleting {}. Retrying.", default, request.appId)
            requeue(request.copy(failures = request.failures + 1))
            ()
        }
      } catch {
        case e: Exception =>
          logger.error("Encountered exception when trying to delete Marathon app for %s. Retrying.".format(request.appId.toString), e)
          requeue(request.copy(failures = request.failures + 1))

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
    logger.info("Starting the janitor...")
    executor.submit(new Janitor())
    ()
  }

  def stop(): Unit = {
    logger.info("Stopping the janitor...")
    running = false
    executor.shutdown()
  }
}

object MarathonSdkJanitor {
  val MaximumFailures: Int = 5
  val TimeBetweenChecksMilliseconds: Int = 10000
  val SdkApiVersionLabel = "DCOS_COMMONS_API_VERSION"

  /** Encapsulate the meat of a request. */
  case class JanitorRequest(appId: AppId,
                            session: RequestSession,
                            failures: Int,
                            created: Long,
                            lastAttempt: Long) extends Delayed {

    override def compareTo(o: Delayed): Int = {
      getDelay(TimeUnit.MILLISECONDS).compare(o.getDelay(TimeUnit.MILLISECONDS))
    }

    override def getDelay(unit: TimeUnit): Long = {
      unit.convert(TimeBetweenChecksMilliseconds + lastAttempt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    }
  }
}
