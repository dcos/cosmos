package com.mesosphere.cosmos.service

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.error.ServiceAlreadyStarted
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.ServiceDescribeResponse
import com.mesosphere.cosmos.rpc.v1.model.ServiceUpdateResponse
import com.mesosphere.cosmos.rpc.v1.model.UninstallResponse
import com.mesosphere.cosmos.rpc.v2.model.InstallResponse
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.trimContentForPrinting
import com.mesosphere.error.ResultOps
import com.mesosphere.universe
import com.mesosphere.universe.v5.model.Manager
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.util.Future

class CustomPackageManagerRouter(adminRouter: AdminRouter, packageCollection: PackageCollection) {

  private[this] lazy val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def getCustomPackageManagerId(
    managerId: Option[String],
    packageName: Option[String],
    packageVersion: Option[universe.v3.model.Version],
    appId: Option[AppId]
  )(
    implicit session: RequestSession
  ): Future[Option[String]] = {
    managerId match {
      case Some(_) => Future(managerId)
      case None =>
        (packageName, packageVersion, appId) match {
          case (Some(name), Some(version), _) =>
            getPackageManagerWithNameAndVersion(name, Option(version)).map(_.map(_.packageName))
          case (Some(name), None, _) =>
            getPackageManagerWithNameAndVersion(name, None).map(_.map(_.packageName))
          case (None, None, Some(id)) =>
            getPackageNameAndVersionFromMarathonApp(id)
              .flatMap {
                case (Some(pkgName), Some(pkgVersion)) =>
                  getPackageManagerWithNameAndVersion(pkgName, Option(pkgVersion)).map(_.map(_.packageName))
                case _ => Future(None)
              }
          case _ => Future(None)
        }
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
      request.appId,
      managerId = None
    )
    adminRouter
      .postCustomPackageInstall(
        AppId(managerId),
        new rpc.v1.model.InstallRequest(
          request.packageName,
          request.packageVersion,
          request.options,
          request.appId,
          managerId = None
        )
      )
      .map { response =>
        validateResponse(response)
        decode[InstallResponse](response.contentString).getOrThrow
      }
  }

  def callCustomPackageUninstall(
    request: rpc.v1.model.UninstallRequest,
    managerId: String
  )(implicit session: RequestSession): Future[UninstallResponse] = {
    adminRouter.getApp(AppId(managerId))
    adminRouter
      .postCustomPackageUninstall(
        AppId(managerId),
        new rpc.v1.model.UninstallRequest(
          request.packageName,
          request.appId,
          request.all,
          packageVersion = request.packageVersion,
          managerId = None
        )
      )
      .map { response =>
        validateResponse(response)
        decode[UninstallResponse](response.contentString).getOrThrow
      }
  }

  def callCustomServiceDescribe(
    request: rpc.v1.model.ServiceDescribeRequest,
    managerId: String
  )(
    implicit session: RequestSession
  ): Future[ServiceDescribeResponse] = {
    adminRouter.getApp(AppId(managerId))
    adminRouter
      .postCustomServiceDescribe(
        AppId(managerId),
        new rpc.v1.model.ServiceDescribeRequest(
          request.appId,
          packageName = request.packageName,
          packageVersion = request.packageVersion,
          managerId = None
        )
      )
      .map { response =>
        validateResponse(response)
        decode[ServiceDescribeResponse](response.contentString).getOrThrow
      }
  }

  def callCustomServiceUpdate(
    request: rpc.v1.model.ServiceUpdateRequest,
    managerId: String
  )(
    implicit session: RequestSession
  ): Future[ServiceUpdateResponse] = {
    adminRouter.getApp(AppId(managerId))
    adminRouter
      .postCustomServiceUpdate(
        AppId(managerId),
        new rpc.v1.model.ServiceUpdateRequest(
          request.appId,
          request.packageVersion,
          request.options,
          request.replace,
          packageName = request.packageName,
          managerId = None
        )
      )
      .map { response =>
        validateResponse(response)
        decode[ServiceUpdateResponse](response.contentString).getOrThrow
      }
  }

  private def getPackageNameAndVersionFromMarathonApp(
    appId: AppId
  )(
    implicit session: RequestSession
  ): Future[(Option[String], Option[universe.v3.model.Version])] = {
    adminRouter
      .getApp(appId)
      .map { appResponse =>
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
    packageVersion: Option[com.mesosphere.universe.v3.model.Version]
  )(
    implicit session: RequestSession
  ): Future[Option[Manager]] = {
    packageCollection
      .getPackageByPackageVersion(packageName, packageVersion)
      .map { case (pkg, _) => pkg.pkgDef.manager }
  }
}
