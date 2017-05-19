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
import org.slf4j.Logger

trait Worker extends Runnable {
  def stop(): Unit
}

/** The work thread used by SdkJanitor */
final class JanitorWorker(
  queue: DelayQueue[Request],
  tracker: Tracker,
  adminRouter: AdminRouter
                         ) extends Worker {

  @volatile var running: Boolean = true
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def stop(): Unit = {
    running = false
    val _ = queue.add(ShutdownRequest())
  }

  override def run(): Unit = {
    logger.info("Starting work loop...")
    while (running) {
      try {
        logger.info("Queuing for work.")
        queue.take() match {
          case janitorRequest: JanitorRequest => doWork(janitorRequest)
          case _: ShutdownRequest => ()
        }
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
      case e: Exception =>
        logger.error("Encountered exception during uninstall evaluation.", e)
        requeue(request.copy(failures = "Encountered exception: %s".format(e.getMessage) :: request.failures))
    }
  }

  def checkUninstall(app: MarathonApp)(implicit session: RequestSession): Boolean = {
    logger.info("Checking the status of the uninstall for app: {}", app.id)

    try {
      Await.result(adminRouter.getSdkServicePlanStatus(
        service = app.id.toString,
        apiVersion = app.labels.getOrElse(SdkJanitor.SdkApiVersionLabel, "v1"),
        plan = "deploy"
      )).status == Status.Ok
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
      deleteResult.status match {
        case badRequest if 400 until 499 contains badRequest.code =>
          logger.error("Encountered Marathon error: {} when deleting {}. Failing.", badRequest,
            request.appId)
          fail(request.copy(failures = "Encountered Marathon error: %s".format(badRequest) :: request.failures))
          ()
        case retryRequest if 500 until 599 contains retryRequest.code =>
          logger.error("Encountered Marathon error: {} when deleting {}. Retrying.", retryRequest,
            request.appId)
          requeue(request.copy(failures = "Encountered Marathon error: %s".format(retryRequest) :: request.failures))
          ()
        case Status.Ok =>
          logger.info("Deleted app: {}", request.appId)
          tracker.completeUninstall(request.appId)
          ()
        case default =>
          logger.error("Encountered unexpected status: {} when deleting {}. Retrying.", default, request.appId)
          requeue(request.copy(failures = "Encountered unexpected status: %s".format(default) :: request.failures))
          ()
      }
    } catch {
      case e: Exception =>
        logger.error("Encountered exception when trying to delete Marathon app for %s. Retrying.".format(request.appId.toString), e)
        requeue(request.copy(failures = "Encountered exception: %s".format(e.getMessage) :: request.failures))
    }
  }

  def fail(request: JanitorRequest): Unit = {
    logger.error("Failed to delete app: {} after {} attempts.", request.appId, request.failures)
    tracker.failUninstall(request.appId, request.failures)
  }

  /** Determine if the request should be requeued. If it should, do so. */
  def requeue(request: JanitorRequest): Unit = {
    logger.info("Evaluating if request to delete {} should be added back to the queue.", request.appId)
    if (request.failures.length >= MaximumFailures) {
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
