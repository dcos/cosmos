package com.mesosphere.cosmos.service
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId}
import com.mesosphere.universe.v5.model.Manager
import com.twitter.finagle.http.Response
import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import org.slf4j.Logger

object CustomPackageManagerUtils  {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def requiresCustomPackageManager(
  adminRouter: AdminRouter,
  packageCollection: PackageCollection,
  managerId: Option[String],
  packageName: Option[String],
  packageVersion: Option[universe.v3.model.Version],
  appId: Option[AppId])(implicit session: RequestSession): Future[Boolean] = {
    logger.info("checking if package requires custom manager")
    if (managerId.isDefined) {
      logger.info("managerId is present " + managerId.get)
      return Future{true}
    } else if (packageName.isDefined && packageVersion.isDefined) {
      getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion)
        .map {
          case (manager) =>
           return Future {manager.isDefined}
        }
    } else if (appId.isDefined) {
      getPackageNameAndVersionFromMarathonApp(adminRouter, appId.get)
        .map {
          case (packageName, packageVersion) =>
            getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion)
              .flatMap {
                manager => return Future {manager.isDefined}
              }
          }
      }
    logger.info("conditions not met")
    Future {false}
  }

  def callCustomPackageInstall(
    adminRouter: AdminRouter,
    packageCollection: PackageCollection,
    request: rpc.v2.model.InstallRequest)
    (implicit session: RequestSession): Future[Response] = {
    if (request.managerId.isDefined) {
       logger.info("sending request to " + request.managerId.get)
      adminRouter.getApp(AppId(request.managerId.get));
      val translatedRequest = new rpc.v1.model.InstallRequest(
        request.packageName,
        request.packageVersion,
        request.options,
        request.appId)
      logger.info("posting request to  " + request.managerId.get)
      return adminRouter.postCustomPackageInstall(AppId(request.managerId.get), translatedRequest)
    } else if (request.packageVersion.isDefined) {
        getPackageManagerWithNameAndVersion(
          packageCollection,
          request.packageName,
          request.packageVersion.as[Option[universe.v3.model.Version]])
        .flatMap {
        case manager =>
          val translatedRequest = new rpc.v1.model.InstallRequest(
            request.packageName,
            request.packageVersion,
            request.options,
            request.appId)
          return adminRouter.postCustomPackageInstall(AppId(manager.get.packageName), translatedRequest)
        }
     }
    //empty response for safety
    Future {Response()}
  }

  def callCustomPackageUninstall(
    adminRouter: AdminRouter,
   packageCollection: PackageCollection,
   request: rpc.v2.model.UninstallRequest) (implicit session: RequestSession): Future[Response] = {
    if (request.managerId.isDefined) {
      //first check for managerId and call
      adminRouter.getApp(AppId(request.managerId.get));
      val translatedRequest = new rpc.v1.model.UninstallRequest(
        request.packageName,
        request.appId,
        request.all)
      adminRouter.postCustomPackageUninstall(AppId(request.managerId.get), translatedRequest)
    } else if (request.packageVersion.isDefined) {
      //if manager Id is not specified but package name and package version are
      //use package name and package version to pull manager from pkgDef
      getPackageManagerWithNameAndVersion(
          packageCollection,
          request.packageName,
          request.packageVersion.as[Option[universe.v3.model.Version]])
        .flatMap {
          case manager =>
            val translatedRequest = new rpc.v1.model.UninstallRequest(
              request.packageName,
              request.appId,
              request.all)
            adminRouter.postCustomPackageUninstall(AppId(manager.get.packageName), translatedRequest)
        }
    } else if (request.appId.isDefined) {
      //if package name and package def are not defined
      //use marathon app id to query marathon app for pkg name/version
      //use package name/version to query for pkg def
      getPackageNameAndVersionFromMarathonApp(adminRouter, request.appId.get)
        .map {
          case (packageName, packageVersion) =>
            getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion)
              .flatMap {
                case manager =>
                  val translatedRequest = new rpc.v1.model.UninstallRequest(
                    request.packageName,
                    request.appId,
                    request.all)
                  adminRouter.postCustomPackageUninstall(AppId(manager.get.packageName), translatedRequest)
              }
        }
    }

    //empty response
    Future { Response()}
  }

  def callCustomServiceDescribe(adminRouter: AdminRouter, packageCollection: PackageCollection,
                                request: rpc.v2.model.ServiceDescribeRequest) (implicit session: RequestSession): Future[Response] = {
    if (request.managerId.isDefined) {
      adminRouter.getApp(AppId(request.managerId.get));
      val translatedRequest = new rpc.v1.model.ServiceDescribeRequest(request.appId)
      adminRouter.postCustomServiceDescribe(AppId(request.managerId.get), translatedRequest)
    } else if (request.packageVersion.isDefined && request.packageName.isDefined) {
      getPackageManagerWithNameAndVersion(
        packageCollection,
        request.packageName.get,
        request.packageVersion.as[Option[universe.v3.model.Version]])
        .flatMap {
          case manager =>
            val translatedRequest = new rpc.v1.model.ServiceDescribeRequest(request.appId)
            adminRouter.postCustomServiceDescribe(AppId(manager.get.packageName), translatedRequest)
        }
    } else {
      //app Id always defined
      getPackageNameAndVersionFromMarathonApp(adminRouter, request.appId)
        .map {
          case (packageName, packageVersion) =>
            getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion)
              .flatMap {
                case manager =>
                  val translatedRequest = new rpc.v1.model.ServiceDescribeRequest(request.appId)
                  adminRouter.postCustomServiceDescribe(AppId(manager.get.packageName), translatedRequest)
              }
        }
    }
    Future {Response()}
  }

  def callCustomServiceUpdate(adminRouter: AdminRouter, packageCollection: PackageCollection,
                              request: rpc.v2.model.ServiceUpdateRequest) (implicit session: RequestSession): Future[Response] = {
    if (request.managerId.isDefined) {
      adminRouter.getApp(AppId(request.managerId.get));
      val translatedRequest = new rpc.v1.model.ServiceUpdateRequest(
        request.appId,
        request.packageVersion,
        request.options,
        request.replace)
      adminRouter.postCustomServiceUpdate(AppId(request.managerId.get), translatedRequest)
    } else if (request.packageVersion.isDefined && request.packageName.isDefined) {
      getPackageManagerWithNameAndVersion(
        packageCollection,
        request.packageName.get,
        request.packageVersion)
        .flatMap {
          case manager =>
            val translatedRequest = new rpc.v1.model.ServiceUpdateRequest(
              request.appId,
              request.packageVersion,
              request.options,
              request.replace)
            adminRouter.postCustomServiceUpdate(AppId(manager.get.packageName), translatedRequest)
        }
    } else {
      //app Id always defined
      getPackageNameAndVersionFromMarathonApp(adminRouter, request.appId)
        .map {
          case (packageName, packageVersion) =>
            getPackageManagerWithNameAndVersion(packageCollection, packageName.get, packageVersion)
              .flatMap {
                case manager =>
                  val translatedRequest = new rpc.v1.model.ServiceUpdateRequest(
                    request.appId,
                    request.packageVersion,
                    request.options,
                    request.replace)
                  adminRouter.postCustomServiceUpdate(AppId(manager.get.packageName), translatedRequest)
              }
        }
    }
    Future {Response()}
  }

  private def getPackageNameAndVersionFromMarathonApp(adminRouter: AdminRouter, appId: AppId) (implicit session: RequestSession):
  Future[(Option[String], Option[universe.v3.model.Version])] = {
    adminRouter.getApp(appId)
      .flatMap {
        case (appResponse) =>
          val packageName = appResponse.app.packageName
          val packageVersion = appResponse.app.packageVersion
          Future {(packageName, packageVersion)}
      }
  }

  private def getPackageManagerWithNameAndVersion(
    packageCollection: PackageCollection,
    packageName: String,
    packageVersion: Option[com.mesosphere.universe.v3.model.Version],
     ) (implicit session: RequestSession) : Future[Option[Manager]] = {
    packageCollection.getPackageByPackageVersion(
      packageName,
      packageVersion
    ).map {
      case (pkg, _) =>
        pkg.pkgDef.manager
    }
  }
}
