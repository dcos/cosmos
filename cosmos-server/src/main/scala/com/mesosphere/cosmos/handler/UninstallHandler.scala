package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.{AdminRouter, AmbiguousAppId, IncompleteUninstall, MarathonAppDeleteError, MultipleFrameworkIds,
  PackageNotInstalled, SDKJanitor, ServiceUnavailable, UninstallNonExistentAppForPackage, rpc}
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import io.circe.Json
import org.slf4j.Logger

private[cosmos] final class UninstallHandler(
  adminRouter: AdminRouter,
  packageCache: PackageCollection,
  sDKJanitor: SDKJanitor
) extends EndpointHandler[rpc.v1.model.UninstallRequest, rpc.v1.model.UninstallResponse] {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val SDK_SERVICE_LABEL = "DCOS_COMMONS_API_VERSION"
  val SDK_UNINSTALL_ENVVAR = "SDK_UNINSTALL"

  private type FwIds = List[String]

  override def apply(
                req: rpc.v1.model.UninstallRequest
              )(
                implicit session: RequestSession
              ): Future[rpc.v1.model.UninstallResponse] = {
    getMarathonApps(req.packageName, req.appId)
      .map(apps => createUninstallOperations(req.packageName, apps))
      .map { uninstallOps =>
        req.all match {
          case Some(true) => uninstallOps
          case _ if uninstallOps.size > 1 => throw AmbiguousAppId(req.packageName, uninstallOps.map(_.appId))
          case _ => uninstallOps
        }
      }
      .flatMap(runUninstalls)
      .flatMap { uninstallDetails =>
        Future.collect(
          uninstallDetails.map { detail =>
            packageCache.getPackageByPackageVersion(detail.packageName, None)
              .map { case (pkg, _) =>
                detail -> pkg.postUninstallNotes
              }
          }
        )
      }
      .map { detailsAndNotes =>
        val results = detailsAndNotes.map { case (detail, postUninstallNotes) =>
          rpc.v1.model.UninstallResult(
            detail.packageName,
            detail.appId,
            detail.packageVersion,
            postUninstallNotes
          )
        }
        rpc.v1.model.UninstallResponse(results.toList)
      }
  }

  private def runUninstalls(uninstallOps: List[UninstallOperation])
                   (implicit session: RequestSession): Future[Seq[UninstallDetails]] = {
    val futures = for {
      op <- uninstallOps
      appId = op.appId
    } yield {
      op match {
        case _: MarathonUninstallOperation => runMarathonUninstall(op, appId)
        case _: SdkUninstallOperation => runSdkUninstall(op, appId)
      }
    }

    Future.collect(futures)
  }

  private def runMarathonUninstall(op: UninstallOperation, appId: AppId)
                          (implicit session: RequestSession): Future[UninstallDetails] = {
    destroyMarathonApp(appId)(session = session) flatMap { _ =>
      op.frameworkName match {
        case Some(fwName) =>
          lookupFrameworkIds(fwName).flatMap {
            case Nil =>
              Future.value(UninstallDetails.from(op))
            case fwId :: Nil =>
              adminRouter.tearDownFramework(fwId)
                .map { _ =>
                  UninstallDetails.from(op).copy(frameworkId = Some(fwId))
                }
            case all =>
              throw MultipleFrameworkIds(op.packageName, op.packageVersion, fwName, all)
          }
            .handle {
              case su: ServiceUnavailable =>
                throw IncompleteUninstall(op.packageName, su)
            }
        case None =>
          Future.value(UninstallDetails.from(op))
      }
    }
  }

  private def runSdkUninstall(op: UninstallOperation, appId: AppId)
                             (implicit session: RequestSession): Future[UninstallDetails] = {
    adminRouter.getRawApp(appId)
      .map { appJson =>
        appJson.apply("env")
        appJson.remove("uris")
          .remove("version")
          .add("env", Json.fromJsonObject(appJson.apply("env").get.asObject.get.add(SDK_UNINSTALL_ENVVAR, Json.fromString(""))))
      }
      .flatMap(appJson => adminRouter.updateApp(appId,appJson))
      .onSuccess { _ =>
        sDKJanitor.delete(appId, session)
      }.map { response =>
        response.status match {
          case Status.Ok => MarathonAppDeleteSuccess
          case _ =>
            logger.error("Encountered error in marathon request {}", response.contentString)
            throw MarathonAppDeleteError(appId)
        }
      }.flatMap( _ => Future.value(UninstallDetails.from(op)))
  }

  private def lookupFrameworkIds(
    fwName: String
  )(
    implicit session: RequestSession
  ): Future[FwIds] = {
    adminRouter.getFrameworks(fwName).map(_.map(_.id))
  }

  private def destroyMarathonApp(
    appId: AppId
  )(
    implicit session: RequestSession
  ): Future[MarathonAppDeleteSuccess] = {
    adminRouter.deleteApp(appId, force = true) map { resp =>
      resp.status match {
        case Status.Ok => MarathonAppDeleteSuccess()
        case a => throw MarathonAppDeleteError(appId)
      }
    }
  }

  private[this] def getMarathonApps(
    packageName: String,
    appId: Option[AppId]
  )(
    implicit session: RequestSession
  ): Future[List[MarathonApp]] = {
    appId match {
      case Some(id) =>
        adminRouter.getAppOption(id).map {
          case Some(appResponse) => List(appResponse.app)
          case _ => throw UninstallNonExistentAppForPackage(packageName, id)
        }
      case None =>
        adminRouter.listApps().map(_.apps)
    }
  }

  private[this] def createUninstallOperations(
    requestedPackageName: String,
    apps: List[MarathonApp]
  ): List[UninstallOperation] = {
    val uninstallOperations = for {
      app <- apps
      labels = app.labels
      packageName <- labels.get(MarathonApp.nameLabel)
      if packageName == requestedPackageName
    } yield {

      if (labels.get(SDK_SERVICE_LABEL) == Some("v1")) {
        SdkUninstallOperation(appId = app.id,
          packageName = packageName,
          packageVersion = app.packageVersion.as[Option[universe.v2.model.PackageDetailsVersion]],
          frameworkName = labels.get(MarathonApp.frameworkNameLabel))
      } else {
        MarathonUninstallOperation(
          appId = app.id,
          packageName = packageName,
          packageVersion = app.packageVersion.as[Option[universe.v2.model.PackageDetailsVersion]],
          frameworkName = labels.get(MarathonApp.frameworkNameLabel)
        )
      }
    }

    if (uninstallOperations.isEmpty) {
      throw new PackageNotInstalled(requestedPackageName)
    }
    uninstallOperations
  }

  private case class MarathonAppDeleteSuccess()

  private abstract class UninstallOperation {
    val appId: AppId
    val packageName: String
    val packageVersion: Option[universe.v2.model.PackageDetailsVersion]
    val frameworkName: Option[String]
  }
  private case class MarathonUninstallOperation(
                                                 appId: AppId,
                                                 packageName: String,
                                                 packageVersion: Option[universe.v2.model.PackageDetailsVersion],
                                                 frameworkName: Option[String]
                                               ) extends UninstallOperation
  private case class SdkUninstallOperation(
                                            appId: AppId,
                                            packageName: String,
                                            packageVersion: Option[universe.v2.model.PackageDetailsVersion],
                                            frameworkName: Option[String]
                                          ) extends UninstallOperation

  private case class UninstallDetails(
    appId: AppId,
    packageName: String,
    packageVersion: Option[universe.v2.model.PackageDetailsVersion],
    frameworkName: Option[String] = None,
    frameworkId: Option[String] = None
  )
  private case object UninstallDetails {
    def from(uninstallOperation: UninstallOperation): UninstallDetails = {
      UninstallDetails(
        appId = uninstallOperation.appId,
        packageName = uninstallOperation.packageName,
        packageVersion = uninstallOperation.packageVersion,
        frameworkName = uninstallOperation.frameworkName
      )
    }
  }
}
