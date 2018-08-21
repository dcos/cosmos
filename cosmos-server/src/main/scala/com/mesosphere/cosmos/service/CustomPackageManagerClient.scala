package com.mesosphere.cosmos.service

import com.mesosphere.universe.v5.model.Manager
import com.mesosphere.cosmos.{AdminRouter, rpc, trimContentForPrinting}
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc.v2.model.InstallResponse
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.twitter.util.Future
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.error.ServiceAlreadyStarted
import com.mesosphere.cosmos.rpc.v1.model.{ServiceDescribeResponse, ServiceUpdateResponse, UninstallResponse}
import com.mesosphere.error.ResultOps
import com.twitter.finagle.http.{Response, Status}
import org.slf4j.Logge8


object CustomPackageManagerClient  {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def getCustomPackageManagerId(
    adminRouter: AdminRouter,
    packageCollection: PackageCollection,
    managerId: Option[String],
    packageName: Option[String],
    packageVersion: Option[universe.v3.model.Version],
    appId: Option[AppId]
  )(implicit session: RequestSession): Future[String] = {

    (managerId, packageName, packageVersion, appId) match {
      case (Some(id), _, _, _) =>
        Future(id)
      case (None, Some(name), Some(version), _) =>
        getPackageManagerWithNameAndVersion(packageCollection, name, version)
          .flatMap {
            case Some(manager) =>
              Future (manager.packageName)
            case None  =>
              Future ("")
          }
      case (None, None, None, Some(id)) =>
        getPackageNameAndVersionFromMarathonApp(adminRouter, id)
          .flatMap {
            case (packageName, packageVersion) =>
              getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion.get)
                .flatMap {
                  case Some(manager) =>
                    Future (manager.packageName)
                  case None =>
                    Future ("")
                }
            }
      case _ =>
        Future("")
    }
  }

  def callCustomPackageInstall(
    adminRouter: AdminRouter,
    request: rpc.v1.model.InstallRequest,
    managerId: String
  )(implicit session: RequestSession): Future[InstallResponse] = {
    val translatedRequest = new rpc.v1.model.InstallRequest(
        request.packageName,
        request.packageVersion,
        request.options,
        request.appId)
      adminRouter.postCustomPackageInstall(
        AppId(managerId),
        translatedRequest
      ).flatMap {
        response =>
          Future {
            validateResponse(response)
            decode[InstallResponse](response.contentString).getOrThrow
          }
      }
  }

  def callCustomPackageUninstall(
    adminRouter: AdminRouter,
    request: rpc.v1.model.UninstallRequest,
    managerId: String
  )(implicit session: RequestSession): Future[UninstallResponse] = {
      adminRouter.getApp(AppId(request.managerId.get))
      val translatedRequest = new rpc.v1.model.UninstallRequest(
        request.packageName,
        request.appId,
        request.all)
      adminRouter.postCustomPackageUninstall(
        AppId(managerId),
        translatedRequest
      ).flatMap {
        response =>
          Future {
            validateResponse(response)
            decode[UninstallResponse](response.contentString).getOrThrow
          }
      }
  }

  def callCustomServiceDescribe(
   adminRouter: AdminRouter,
   request: rpc.v1.model.ServiceDescribeRequest,
   managerId: String
  )(implicit session: RequestSession): Future[ServiceDescribeResponse] = {
      adminRouter.getApp(AppId(request.managerId.get))
      val translatedRequest = new rpc.v1.model.ServiceDescribeRequest(request.appId)
      adminRouter.postCustomServiceDescribe(
        AppId(managerId),
        translatedRequest
      ).flatMap {
        response =>
          Future {
            validateResponse(response)
            decode[ServiceDescribeResponse](response.contentString).getOrThrow
          }
      }
  }

  def callCustomServiceUpdate(
     adminRouter: AdminRouter,
     request: rpc.v1.model.ServiceUpdateRequest,
     managerId: String
   )(implicit session: RequestSession): Future[ServiceUpdateResponse] = {
      adminRouter.getApp(AppId(request.managerId.get))
      val translatedRequest = new rpc.v1.model.ServiceUpdateRequest(
        request.appId,
        request.packageVersion,
        request.options,
        request.replace)
      adminRouter.postCustomServiceUpdate(
        AppId(managerId),
        translatedRequest
      ).flatMap {
        response =>
          Future {
            validateResponse(response)
            decode[ServiceUpdateResponse](response.contentString).getOrThrow
          }
      }

  }

  private def getPackageNameAndVersionFromMarathonApp(
   adminRouter: AdminRouter,
   appId: AppId
  )(implicit session: RequestSession): Future[(Option[String], Option[universe.v3.model.Version])] = {
    adminRouter.getApp(appId)
      .flatMap {
        appResponse =>
          val packageName = appResponse.app.packageName
          val packageVersion = appResponse.app.packageVersion
          Future {(packageName, packageVersion)}
      }
  }

  private def validateResponse(response: Response): Unit = {
    response.status match {
      case Status.Conflict =>
        throw ServiceAlreadyStarted().exception
      case status if (400 until 500).contains(status.code) =>
        logger.warn(s"Custom manager returned [${status.code}]: " +
          s"${trimContentForPrinting(response.contentString)}")
      case status if (500 until 600).contains(status.code) =>
        logger.warn(s"Custom manager is unavailable [${status.code}]: " +
          s"${trimContentForPrinting(response.contentString)}")
      case status =>
        logger.warn(s"Custom manager responded with [${status.code}]: " +
          s"${trimContentForPrinting(response.contentString)}")
    }
  }

  private def getPackageManagerWithNameAndVersion(
    packageCollection: PackageCollection,
    packageName: String,
    packageVersion: com.mesosphere.universe.v3.model.Version
 )(implicit session: RequestSession) : Future[Option[Manager]] = {
    packageCollection.getPackageByPackageVersion(
      packageName,
      Option(packageVersion)
    ).map {
      case (pkg, _) =>
        pkg.pkgDef.manager
    }
  }
}
