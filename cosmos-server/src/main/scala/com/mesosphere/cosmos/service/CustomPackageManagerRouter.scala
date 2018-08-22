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
import com.mesosphere.cosmos.rpc.v1.model.ServiceDescribeResponse
import com.mesosphere.cosmos.rpc.v1.model.UninstallResponse
import com.mesosphere.cosmos.rpc.v1.model.ServiceUpdateResponse
import com.mesosphere.error.ResultOps
import com.twitter.finagle.http.{Response, Status}
import org.slf4j.Logger


class CustomPackageManagerRouter(adminRouter: AdminRouter, packageCollection: PackageCollection)  {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def getCustomPackageManagerId(
    managerId: Option[String],
    packageName: Option[String],
    packageVersion: Option[universe.v3.model.Version],
    appId: Option[AppId]
 )(implicit session: RequestSession): Future[Option[String]] = {
    val defaultId = Some("")
    (managerId, packageName, packageVersion, appId) match {
      case (Some(id), _, _, _) =>
        Future(Some(id))
      case (None, Some(name), Some(version), _) =>
        getPackageManagerWithNameAndVersion(name, version)
          .flatMap {
            case Some(manager) =>
              Future (Some(manager.packageName))
            case None  =>
              Future (defaultId)
          }
      case (None, None, None, Some(id)) =>
        getPackageNameAndVersionFromMarathonApp(id)
          .flatMap {
            case (packageName, packageVersion) =>
              getPackageManagerWithNameAndVersion(packageName.get, packageVersion.get)
                .flatMap {
                  case Some(manager) =>
                    Future (Some(manager.packageName))
                  case None =>
                    Future (defaultId)
                }
            }
      case _ =>
        Future(defaultId)
    }
  }

  def callCustomPackageInstall(
    request: rpc.v1.model.InstallRequest,
    managerId: String
  )(implicit session: RequestSession): Future[InstallResponse] = {
    new rpc.v1.model.InstallRequest(
        request.packageName,
        request.packageVersion,
        request.options,
        request.appId)
      adminRouter.postCustomPackageInstall(
        AppId(managerId),
        new rpc.v1.model.InstallRequest(
          request.packageName,
          request.packageVersion,
          request.options,
          request.appId
        )
      ).map {
        response =>
          validateResponse(response)
          decode[InstallResponse](response.contentString).getOrThrow
      }
  }

  def callCustomPackageUninstall(
    request: rpc.v1.model.UninstallRequest,
    managerId: String
  )(implicit session: RequestSession): Future[UninstallResponse] = {
    adminRouter.getApp(AppId(request.managerId.get))
    adminRouter.postCustomPackageUninstall(
      AppId(managerId),
      new rpc.v1.model.UninstallRequest(
        request.packageName,
        request.appId,
        request.all
      )
    ).map {
      response =>
        validateResponse(response)
        decode[UninstallResponse](response.contentString).getOrThrow
    }
  }

  def callCustomServiceDescribe(
   request: rpc.v1.model.ServiceDescribeRequest,
   managerId: String
  )(implicit session: RequestSession): Future[ServiceDescribeResponse] = {
    adminRouter.getApp(AppId(request.managerId.get))
    adminRouter.postCustomServiceDescribe(
      AppId(managerId),
      new rpc.v1.model.ServiceDescribeRequest(request.appId)
    ).map {
      response =>
        validateResponse(response)
        decode[ServiceDescribeResponse](response.contentString).getOrThrow
    }
  }

  def callCustomServiceUpdate(
     request: rpc.v1.model.ServiceUpdateRequest,
     managerId: String
   )(implicit session: RequestSession): Future[ServiceUpdateResponse] = {
      adminRouter.getApp(AppId(request.managerId.get))
      adminRouter.postCustomServiceUpdate(
        AppId(managerId),
        new rpc.v1.model.ServiceUpdateRequest(
        request.appId,
        request.packageVersion,
        request.options,
        request.replace
        )
      ).map {
        response =>
          validateResponse(response)
          decode[ServiceUpdateResponse](response.contentString).getOrThrow
      }
  }

  private def getPackageNameAndVersionFromMarathonApp(
   appId: AppId
  )(implicit session: RequestSession): Future[(Option[String], Option[universe.v3.model.Version])] = {
    adminRouter.getApp(appId)
      .map {
        appResponse =>
          val packageName = appResponse.app.packageName
          val packageVersion = appResponse.app.packageVersion
          (packageName, packageVersion)
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
