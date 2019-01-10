package com.mesosphere.cosmos.service

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.error._
import com.mesosphere.cosmos.handler.UninstallHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonAppResponse}
import com.twitter.conversions.time._
import com.twitter.finagle.http.Status
import com.twitter.util.{Duration, Return, Throw, Timer, Future => TwitterFuture, Promise => TwitterPromise}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => ScalaFuture, Promise => ScalaPromise}
import scala.util.{Failure, Success}

final class ServiceUninstaller(
  adminRouter: AdminRouter
)(
  implicit timer: Timer
) {

  import ServiceUninstaller._
  private[this] val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  // scalastyle:off
  def uninstall(
                 appId: AppId,
                 frameworkIds: Set[String],
                 deploymentId: String,
                 retries: Int = ServiceUninstaller.DefaultRetries
               )(implicit session: RequestSession): TwitterFuture[Unit] = async {
    // 1. check that deployment of SDK_UNINSTALL label is finished
    val deploymentCompleted = await(checkDeploymentCompleted(deploymentId)(session))
    if (deploymentCompleted) {
      // 2. check that SDK scheduler is running with SDK_UNINSTALL label
      val maybeSchedulerApp = await(getApp(appId)(session))
      maybeSchedulerApp match {
        case Some(schedulerApp) =>
          val schedulerVersion = schedulerApp.app.labels.getOrElse(UninstallHandler.SdkVersionLabel, "v1")
          val sdkUninstallLabelPresent = schedulerApp.app.labels.getOrElse(UninstallHandler.SdkUninstallEnvvar, "false").toBoolean

          if (sdkUninstallLabelPresent) {
            // 3. check that SDK scheduler uninstall plan finished
            val uninstallComplete = await(checkSdkUninstallCompleted(
              appId,
              frameworkIds,
              apiVersion = schedulerVersion
            )(session))
            if (uninstallComplete) {
              // 4. delete the scheduler from marathon
              val deleted = await(delete(appId)(session))
              if (!deleted) {
                logger.info(s"Trying to delete $appId from Marathon, but it failed. Retrying...")
                await(retry(appId, frameworkIds, deploymentId, retries, MarathonAppDeleteError(appId))(session))
              }
            } else {
              logger.info(s"Waiting for uninstall plan on scheduler $appId to be finished, but it's still running. Retrying...")
              await(retry(appId, frameworkIds, deploymentId, retries, MarathonDeploymentNotFinished(deploymentId))(session))
            }
          } else {
            // The app is already being uninstalled / already uninstalled by some other thread.
            logger.warn("Marking uninstall as done because scheduler does not have SDK_UNINSTALL label anymore. Assuming uninstall finished.")
          }
        case None =>
          // The scheduler is already removed from marathon; stop now to avoid uninstalling a restarted app
          logger.warn("Marking uninstall as done because scheduler does not exist anymore in Marathon - nothing to uninstall.")
      }
    } else {
      logger.info(s"Waiting for deployment $deploymentId to be finished, but it's still running. Retrying...")
      await(retry(appId, frameworkIds, deploymentId, retries, SdkUninstallNotComplete(appId))(session))
    }

    ()
  }.recoverWith {
    case ex =>
      logger.warn(s"Unexpected error when uninstalling a package $appId. $ex")
      retry(appId, frameworkIds, deploymentId, retries, ErrorDuringUninstall(ex.toString))(session)
  }.asTwitter
  // scalastyle:on

  private def getApp(appId: AppId)(
    implicit session: RequestSession
  ): ScalaFuture[Option[MarathonAppResponse]] = {
    adminRouter.getApp(appId).asScala.map(Some(_)).recover {
      case ex: CosmosException if ex.error.isInstanceOf[MarathonAppNotFound] =>
        None
    }
  }

  private def retry(appId: AppId, frameworkIds: Set[String], deploymentId: String, retries: Int, cosmosError: CosmosError)(
    implicit session: RequestSession
  ): ScalaFuture[Unit] = {
    if (retries > 0) {
      TwitterFuture.sleep(RetryInterval)
        .before(uninstall(appId, frameworkIds, deploymentId, retries - 1)).asScala
    } else {
      ScalaFuture.failed(CosmosException.apply(cosmosError))
    }
  }

  private def checkDeploymentCompleted(
    deploymentId: String
  )(
    implicit session: RequestSession
  ): ScalaFuture[Boolean] = {
    for {
      deployments <- adminRouter.listDeployments()
    } yield {
      val completed = !deployments.exists(_.id == deploymentId)
      val status = if (completed) "complete" else "still in progress"
      logger.info(s"Deployment $status. Id: $deploymentId")
      completed
    }
  }.asScala

  private def checkSdkUninstallCompleted(
    appId: AppId,
    frameworkIds: Set[String],
    apiVersion: String
  )(
    implicit session: RequestSession
  ): ScalaFuture[Boolean] = {
    val deployed = adminRouter.getSdkServicePlanStatus(
      appId,
      apiVersion,
      "deploy"
    ).map(_.status == Status.Ok)

    val sameFrameworkIds = adminRouter.getSdkServiceFrameworkIds(appId, apiVersion)
      .map(_.toSet == frameworkIds)
      // If we can't read the IDs, assume it's because the app is ready to be deleted
      .handle { case _ => true }

    deployed.joinWith(sameFrameworkIds)(_ && _)
  }.asScala

  private def delete(
    appId: AppId
  )(
    implicit session: RequestSession
  ): ScalaFuture[Boolean] = {
    adminRouter.deleteApp(appId).map { response =>
      if (response.status.code >= 400 && response.status.code < 500) {
        logger.error(
          s"Encountered Marathon error : ${response.status} when deleting $appId. Giving up."
        )
        true
      } else if (response.status == Status.Ok) {
        logger.info(s"Deleted app: $appId")
        true
      } else {
        false
      }
    }.asScala
  }
}

object ServiceUninstaller {
  def apply(
    adminRouter: AdminRouter
  )(
    implicit timer: Timer
  ): ServiceUninstaller = {
    new ServiceUninstaller(adminRouter)
  }

  val RetryInterval: Duration = 10.seconds

  private val DefaultRetries: Int = 10000

  implicit class RichTwitterFuture[A](val tf: TwitterFuture[A]) extends AnyVal {
    def asScala: ScalaFuture[A] = {
      val promise: ScalaPromise[A] = ScalaPromise()
      tf.respond {
        case Return(value) => promise.success(value)
        case Throw(exception) => promise.failure(exception)
      }
      promise.future
    }
  }

  implicit class RichScalaFuture[A](val sf: ScalaFuture[A]) extends AnyVal {
    def asTwitter: TwitterFuture[A] = {
      val promise: TwitterPromise[A] = new TwitterPromise[A]()
      sf.onComplete {
        case Success(value) => promise.setValue(value)
        case Failure(exception) => promise.setException(exception)
      }
      promise
    }
  }
}
