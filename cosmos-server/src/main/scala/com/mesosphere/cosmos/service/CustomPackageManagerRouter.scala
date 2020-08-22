package com.mesosphere.cosmos.service

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.CustomPackageManagerNotFound
import com.mesosphere.cosmos.error.CustomPackageManagerError
import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.ServiceAlreadyStarted
import com.mesosphere.cosmos.error.VersionNotFound
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
      case Some(_) => Future(Some((managerId, None, None)))
      case None =>
        (packageName, packageVersion, appId) match {
          case (Some(pkgName), Some(pkgVersion), mayBeAppId) =>
            getPackageManagerWithNameAndVersion(pkgName, pkgVersion, mayBeAppId)
              .map(_.map(manager => (Some(manager.packageName), Some(pkgName), Some(pkgVersion))))
          case (_, _, Some(id)) =>
            getPackageNameAndVersionFromMarathonApp(id)
              .flatMap {
                case (Some(pkgName), Some(pkgVersion)) =>
                  getPackageManagerWithNameAndVersion(pkgName, pkgVersion, Some(id))
                    .map(_.map(manager => (Some(manager.packageName), Some(pkgName), Some(pkgVersion))))
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
        validateResponse(response, managerId)
        decode[InstallResponse](response.contentString).getOrThrow
      }
  }

  def callCustomPackageUninstall(
    request: rpc.v1.model.UninstallRequest,
    managerId: String,
    packageName: String,
    packageVersion: universe.v3.model.Version,
    appId: AppId
  )(implicit session: RequestSession): Future[UninstallResponse] = {
    adminRouter
      .postCustomPackageUninstall(
        AppId(managerId),
        new rpc.v1.model.UninstallRequest(
          packageName,
          Option(appId),
          request.all,
          packageVersion = Option(packageVersion),
          managerId = None
        )
      )
      .map { response =>
        validateResponse(response, managerId)
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
        validateResponse(response, managerId)
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
        validateResponse(response, managerId)
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

  private def validateResponse(response: Response, managerId: String): Unit = {
    response.status match {
      case Status.Conflict =>
        throw ServiceAlreadyStarted().exception
      case Status.NotFound =>
        throw CustomPackageManagerNotFound(AppId(managerId)).exception
      case status if (400 until 500).contains(status.code) =>
        logger.warn(s"Custom manager returned [${status.code}]: " +
          s"${trimContentForPrinting(response.contentString)}")
        throw CustomPackageManagerError(managerId, status.code, response.contentString).exception
      case status if (500 until 600).contains(status.code) =>
        logger.warn(s"Custom manager is unavailable [${status.code}]: " +
          s"${trimContentForPrinting(response.contentString)}")
        throw CustomPackageManagerError(managerId, status.code, response.contentString).exception
      case status =>
        logger.info(s"Custom manager responded with [${status.code}]: " +
          s"${trimContentForPrinting(response.contentString)}")
    }
  }

  private def getPackageManagerWithNameAndVersion(
    packageName: String,
    packageVersion: com.mesosphere.universe.v3.model.Version,
    appId: Option[AppId]
  )(
    implicit session: RequestSession
  ): Future[Option[Manager]] = {
    packageCollection
      .getPackageByPackageVersion(packageName, Option(packageVersion))
      .map { case (pkg, _) => pkg.pkgDef.manager }
      .rescue {
        case CosmosException(PackageNotFound(_),_, _) | CosmosException(VersionNotFound(_, _),_, _) if appId.nonEmpty =>
          adminRouter.getApp(appId.get).map(_.app.packageDefinition.flatMap(_.manager))
      }
  }
}
