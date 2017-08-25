package com.mesosphere.cosmos.service

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.MarathonAppNotFound
import com.mesosphere.cosmos.handler.UninstallHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.twitter.conversions.time._
import com.twitter.finagle.http.Status
import com.twitter.util.Try.PredicateDoesNotObtain
import com.twitter.util.Duration
import com.twitter.util.Future
import com.twitter.util.Timer

final class ServiceUninstaller(
  adminRouter: AdminRouter
)(
  implicit timer: Timer
) {

  import ServiceUninstaller._

  private[this] val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def uninstall(
    appId: AppId,
    frameworkIds: Set[String],
    deploymentId: String,
    retries: Int = ServiceUninstaller.DefaultRetries
  )(
    implicit session: RequestSession
  ): Future[Unit] = {
    val work = for {
      deployed <- checkDeploymentCompleted(deploymentId) if deployed
      marathonResponse <- adminRouter.getApp(appId)
      uninstalled <- checkSdkUninstallCompleted(
        marathonResponse.app.id,
        frameworkIds,
        apiVersion = marathonResponse.app.labels.getOrElse(UninstallHandler.SdkVersionLabel, "v1")
      ) if uninstalled
      deleted <- delete(appId) if deleted
    } yield ()

    work.rescue {
      case ex: CosmosException if ex.error.isInstanceOf[MarathonAppNotFound] =>
        // The app is already gone; stop now to avoid uninstalling a restarted app
        Future.Done
      case ex if retries > 0  =>
        if (!ex.isInstanceOf[PredicateDoesNotObtain]) {
          logger.info(s"Uninstall attempt for $appId didn't finish. $retries retries left." +
            s" Type name: ${ex.getClass.getSimpleName}; Message: ${ex.getMessage}")
        }

        Future.sleep(RetryInterval)
          .before(uninstall(appId, frameworkIds, deploymentId, retries - 1))
    }
  }

  private def checkDeploymentCompleted(
    deploymentId: String
  )(
    implicit session: RequestSession
  ): Future[Boolean] = {
    for {
      deployments <- adminRouter.listDeployments()
    } yield {
      val completed = !deployments.exists(_.id == deploymentId)
      val status = if (completed) "complete" else "still in progress"
      logger.info(s"Deployment $status. Id: $deploymentId")
      completed
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

  private def delete(
    appId: AppId
  )(
    implicit session: RequestSession
  ): Future[Boolean] = {
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

  private val DefaultRetries: Int = 50
}
