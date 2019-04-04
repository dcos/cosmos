package com.mesosphere.cosmos.service

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.error.{CosmosException, MarathonAppNotFound, UninstallFailed}
import com.mesosphere.cosmos.handler.UninstallHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonAppResponse}
import com.twitter.conversions.time._
import com.twitter.finagle.http.Status
import com.twitter.util.{Duration, Future, Timer}

final class ServiceUninstaller(
  adminRouter: AdminRouter
)(
  implicit timer: Timer
) {

  import ServiceUninstaller._
  private[this] val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  // scalastyle:off cyclomatic.complexity
  def uninstall(
    appId: AppId,
    frameworkIds: Set[String],
    deploymentId: String,
    retries: Int = ServiceUninstaller.DefaultRetries
  )(
    implicit session: RequestSession
  ): Future[Unit] = {
    checkDeploymentStep(deploymentId)
      .onStepSuccess(_ => checkSdkUninstallStatusStep(appId, frameworkIds))
      .onStepSuccess(_ => deleteSchedulerStep(appId))
      .onStepSuccess(_ => Future(UninstallDone)) // all steps done -> uninstall is done
      .flatMap {
      case StepSuccess | UninstallDone =>
        // last step was success or one of the steps finished marking the Uninstall as done
        // StepSuccess should actually never happen in this phase but including it to prevent non-exhaustive match
        Future(())
      case Retry =>
        if (retries > 0) {
          logger.info(s"Retrying uninstall of $appId. Remaining number of retries: ${retries - 1}")
          Future.sleep(RetryInterval)
            .before(uninstall(appId, frameworkIds, deploymentId, retries - 1))
        } else {
          logger.error(s"Run out of retries when uninstalling $appId. Uninstall failed.")
          Future.exception(CosmosException(UninstallFailed(appId)))
        }
    }
  }
  // scalastyle:on cyclomatic.complexity

  /**
    * Verifies that the deployment adjusting scheduler app to contain SDK_UNINSTALL label is finished
    * @param deploymentId id of marathon deployment
    */
  private def checkDeploymentStep(
    deploymentId: String
  )(
    implicit session: RequestSession
  ): Future[StepResult] = {
    for {
      deployments <- adminRouter.listDeployments()
    } yield {
      val completed = !deployments.exists(_.id == deploymentId)
      val status = if (completed) "complete" else "still in progress"
      logger.info(s"Verifying marathon deployment $deploymentId during uninstall. Status: $status.")

      if (completed) {
        StepSuccess
      } else {
        Retry
      }
    }
  }.rescue {
    case ex =>
      logger.warn(s"Unexpected exception when checking marathon deployment $deploymentId. Exception: $ex")
      Future(Retry)
  }

  /**
    * Verifies that the scheduler is running with SDK_UNINSTALL label and that the uninstall plan on scheduler is finished
    * @param appId id of the scheduler inside marathon
    */
  private def checkSdkUninstallStatusStep(appId: AppId, frameworkIds: Set[String])(
    implicit session: RequestSession
  ): Future[StepResult] = {
    getApp(appId).flatMap {
      case Some(schedulerApp) =>
        val schedulerVersion = schedulerApp.app.getLabel(UninstallHandler.SdkVersionLabel).getOrElse("v1")
        val sdkUninstallLabelPresent = schedulerApp.app.getEnv(UninstallHandler.SdkUninstallEnvvar).exists(_.toBoolean)
        if (sdkUninstallLabelPresent) {
          checkSdkUninstallCompleted(
            appId,
            frameworkIds,
            apiVersion = schedulerVersion
          ).map {
            case true => StepSuccess
            case _ => Retry
          }
        } else {
          logger.warn(s"No SDK_UNINSTALL label present on app $appId: $schedulerApp")
          Future(Retry)
        }
      case None =>
        // The scheduler is already removed from marathon; stop now to avoid uninstalling a restarted app
        logger.warn("Marking uninstall as done because scheduler does not exist anymore in Marathon - nothing to uninstall.")
        Future(UninstallDone)
    }
  }.rescue {
    case ex =>
      logger.warn(s"Unexpected exception when checking scheduler $appId finished. Exception: $ex")
      Future(Retry)
  }

  private def getApp(appId: AppId)(
    implicit session: RequestSession
  ): Future[Option[MarathonAppResponse]] = {
    adminRouter.getApp(appId).map(Some(_)).rescue {
      case ex: CosmosException if ex.error.isInstanceOf[MarathonAppNotFound] =>
        Future(None)
    }
  }

  private def checkSdkUninstallCompleted(
    appId: AppId,
    frameworkIds: Set[String],
    apiVersion: String
  )(
    implicit session: RequestSession
  ): Future[Boolean] = {
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
  }

  private def deleteSchedulerStep(
    appId: AppId
  )(
    implicit session: RequestSession
  ): Future[StepResult] = {
    adminRouter.deleteApp(appId).map { response =>
      if (response.status.code == 401) {
        logger.error(
          s"Authorization token expired/invalid: ${response.status} when deleting $appId. Giving up."
        )
        UninstallDone
      } else if (response.status.code >= 400 && response.status.code < 500) {
        logger.error(
          s"Encountered Marathon error : ${response.status} when deleting $appId. Giving up."
        )
        UninstallDone
      } else if (response.status == Status.Ok) {
        logger.info(s"Deleted app: $appId")
        UninstallDone
      } else {
        Retry
      }
    }
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

  sealed trait StepResult
  case object StepSuccess extends StepResult
  case object Retry extends StepResult
  case object UninstallDone extends StepResult

  implicit class RichFuture(val currentStep: Future[StepResult]) extends AnyVal {
    def onStepSuccess(stepFunction: Unit => Future[StepResult]): Future[StepResult] = {
      currentStep.flatMap {
        case StepSuccess => stepFunction(())
        case other => Future(other)
      }
    }
  }
}
