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
  ): Future[Option[(Option[String], Option[String], Option[universe.v3.model.Version])]] = {
    managerId match {
      case Some(_) => Future(Some((managerId,None , None)))
      case None =>
        (packageName, packageVersion, appId) match {
          case (Some(name), Some(version), _) =>
            getPackageManagerWithNameAndVersion(name, version).map( manager =>
              Option((Option(manager.get.packageName), Option(name), Option(version))))
          case (_, _, Some(id)) =>
            getPackageNameAndVersionFromMarathonApp(id)
              .flatMap {
                case (Some(pkgName), Some(pkgVersion)) =>
                  getPackageManagerWithNameAndVersion(pkgName, pkgVersion).map(manager =>
                  Option((Option(manager.get.packageName), Option(pkgName), Option(pkgVersion))))
                case _ => Future(Some((None, None, None)))
              }
          case _ => Future(Some((None, None, None)))
        }
    }
  }

  def callCustomPackageInstall(
    request: rpc.v1.model.InstallRequest,
    managerId: String,
    packageName: String
  )(implicit session: RequestSession): Future[InstallResponse] = {
    adminRouter
      .postCustomPackageInstall(
        AppId(managerId),
        new rpc.v1.model.InstallRequest(
          packageName,
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
    managerId: String,
    packageName: String,
    packageVersion: universe.v3.model.Version
  )(implicit session: RequestSession): Future[UninstallResponse] = {
    adminRouter.getApp(AppId(managerId))
    adminRouter
      .postCustomPackageUninstall(
        AppId(managerId),
        new rpc.v1.model.UninstallRequest(
          packageName,
          request.appId,
          request.all,
          packageVersion = Option(packageVersion),
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
    managerId: String,
    packageName: String,
    packageVersion: universe.v3.model.Version
  )(
    implicit session: RequestSession
  ): Future[ServiceDescribeResponse] = {
    adminRouter.getApp(AppId(managerId))
    adminRouter
      .postCustomServiceDescribe(
        AppId(managerId),
        new rpc.v1.model.ServiceDescribeRequest(
          request.appId,
          packageName = Option(packageName),
          packageVersion = Option(packageVersion),
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
    managerId: String,
    packageName: String,
    packageVersion: universe.v3.model.Version
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
          packageName = Option(packageName),
          managerId = None,
          currentPackageVersion = Option(packageVersion)
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
    packageVersion: com.mesosphere.universe.v3.model.Version
  )(
    implicit session: RequestSession
  ): Future[Option[Manager]] = {
    packageCollection
      .getPackageByPackageVersion(packageName, Option(packageVersion))
      .map { case (pkg, _) => pkg.pkgDef.manager }
  }
}
