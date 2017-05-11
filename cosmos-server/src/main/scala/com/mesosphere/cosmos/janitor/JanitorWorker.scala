package com.mesosphere.cosmos.janitor

import java.util.concurrent.DelayQueue

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.MarathonAppNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.janitor.SdkJanitor.JanitorRequest
import com.mesosphere.cosmos.janitor.SdkJanitor._
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import org.apache.http.HttpStatus
import org.slf4j.Logger

trait Worker extends Runnable

/** The work thread used by SdkJanitor */
final class JanitorWorker(
  queue: DelayQueue[JanitorRequest] = new DelayQueue[JanitorRequest](),
  @volatile var running: Boolean,
  tracker: Tracker,
  adminRouter: AdminRouter
                         ) extends Worker {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def run(): Unit = {
    logger.info("Starting work loop...")
    while (running) {
      try {
        logger.info("Queuing for work.")
        val request = queue.take()
        doWork(request)
      } catch {
        case ie: InterruptedException =>
          logger.error("Request queue was interrupted", ie)
        case e: Exception =>
          logger.error("Encountered an exception during execution", e)
      }
    }
  }

  def doWork(request: JanitorRequest): Unit = {
    logger.debug("Took request to delete {}", request.appId)

    logger.info("{} was last checked at {}. Rechecking now...",
      request.appId,
      request.lastAttempt.toString)

    try {
      val app = Await.result(adminRouter.getApp(request.appId)(session = request.session)).app

      if (checkUninstall(app)(request.session)) delete(request) else requeue(request)
    } catch {
      case notFound: MarathonAppNotFound =>
        logger.error("{} was not found in Marathon. Was it already deleted?", notFound.appId)
        tracker.deleteZkRecord(request.appId)
      case e: Exception =>
        logger.error("Encountered exception during uninstall evaluation.", e)
        requeue(request.copy(failures = request.failures + 1))
    }
  }

  def checkUninstall(app: MarathonApp)(implicit session: RequestSession): Boolean = {
    logger.info("Checking the status of the uninstall for app: {}", app.id)

    try {
      Await.result(adminRouter.getSdkServicePlanStatus(
        service = app.id.toString,
        apiVersion = app.labels.getOrElse(SdkJanitor.SdkApiVersionLabel, "v1"),
        plan = "deploy"
      )(session = session)).status match {
        case Status.Ok => true
        case _ => false
      }
    } catch {
      case e: Exception =>
        logger.error("Encountered an exception checking the uninstall progress of %s".format(app.id), e)
        false
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
          logger.info("Deleted app: {}", request.appId)
          tracker.deleteZkRecord(request.appId)
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
    logger.error("Failed to delete app: {} after {} attempts.", request.appId, request.failures)
    tracker.failZkRecord(request.appId)
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
