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
import org.slf4j.Logger


//noinspection ScalaStyle
object CustomPackageManagerClient  {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  // scalastyle:off cyclomatic.complexity

  def getCustomPackageManagerId(
    adminRouter: AdminRouter,
    packageCollection: PackageCollection,
    managerId: Option[String],
    packageName: Option[String],
    packageVersion: Option[universe.v3.model.Version],
    appId: Option[AppId])(implicit session: RequestSession): Future[String] = {
    if (managerId.isDefined) {
       Future{managerId.get}
    } else if (packageName.isDefined && packageVersion.isDefined) {
      getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion)
        .flatMap {
          case manager if manager.isDefined =>
              Future {manager.get.packageName}
          case manager if !manager.isDefined  =>
              Future {""}
        }
    } else if (appId.isDefined) {
      getPackageNameAndVersionFromMarathonApp(adminRouter, appId.get)
        .flatMap {
          case (packageName, packageVersion) =>
            getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion)
              .flatMap {
                case manager if manager.isDefined =>
                  Future {manager.get.packageName}
                case manager if !manager.isDefined  =>
                  Future {""}
              }
          }
      } else {
        Future {""}
      }
  }

  // scalastyle:on cyclomatic.complexity


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
        case response =>
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
      adminRouter.getApp(AppId(request.managerId.get));
      val translatedRequest = new rpc.v1.model.UninstallRequest(
        request.packageName,
        request.appId,
        request.all)
      adminRouter.postCustomPackageUninstall(
        AppId(managerId),
        translatedRequest
      ).flatMap {
        case response =>
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
      adminRouter.getApp(AppId(request.managerId.get));
      val translatedRequest = new rpc.v1.model.ServiceDescribeRequest(request.appId)
      adminRouter.postCustomServiceDescribe(
        AppId(managerId),
        translatedRequest
      ).flatMap {
        case response =>
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
      adminRouter.getApp(AppId(request.managerId.get));
      val translatedRequest = new rpc.v1.model.ServiceUpdateRequest(
        request.appId,
        request.packageVersion,
        request.options,
        request.replace)
      adminRouter.postCustomServiceUpdate(
        AppId(managerId),
        translatedRequest
      ).flatMap {
        case response =>
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
        case (appResponse) =>
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
    packageVersion: Option[com.mesosphere.universe.v3.model.Version]
 )(implicit session: RequestSession) : Future[Option[Manager]] = {
    packageCollection.getPackageByPackageVersion(
      packageName,
      packageVersion
    ).map {
      case (pkg, _) =>
        pkg.pkgDef.manager
    }
  }
}
