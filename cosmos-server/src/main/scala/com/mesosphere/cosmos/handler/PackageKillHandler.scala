package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.internal.model.MarathonAppOps._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.AmbiguousAppId
import com.mesosphere.cosmos.IncompleteKill
import com.mesosphere.cosmos.MarathonAppDeleteError
import com.mesosphere.cosmos.MultipleFrameworkIds
import com.mesosphere.cosmos.PackageNotRunning
import com.mesosphere.cosmos.ServiceUnavailable
import com.mesosphere.cosmos.KillNonExistentAppForPackage
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonApp}
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import com.twitter.util.Future

private[cosmos] final class PackageKillHandler(
  adminRouter: AdminRouter,
  packageCache: PackageCollection
) extends EndpointHandler[rpc.v1.model.KillRequest, rpc.v1.model.KillResponse] {

  private type FwIds = List[String]

  private def lookupFrameworkIds(fwName: String)(implicit session: RequestSession): Future[FwIds] = {
    adminRouter.getMasterState(fwName).map { masterState =>
      masterState.frameworks
        .filter(_.name == fwName)
        .map(_.id)
    }
  }
  private def destroyMarathonAppsAndTearDownFrameworkIfPresent(
    killOperations: List[KillOperation]
  )(implicit session: RequestSession): Future[Seq[KillDetails]] = {
    val fs = for {
      op <- killOperations
      appId = op.appId
    } yield {
      destroyMarathonApp(appId) flatMap { _ =>
        op.frameworkName match {
          case Some(fwName) =>
            lookupFrameworkIds(fwName).flatMap {
              case Nil =>
                Future.value(KillDetails.from(op))
              case fwId :: Nil =>
                adminRouter.tearDownFramework(fwId)
                  .map { _ =>
                    KillDetails.from(op).copy(frameworkId = Some(fwId))
                  }
              case all =>
                throw MultipleFrameworkIds(op.packageName, op.packageVersion, fwName, all)
            }
              .handle {
                case su: ServiceUnavailable =>
                  throw IncompleteKill(op.packageName, su)
              }
          case None =>
            Future.value(KillDetails.from(op))
        }
      }
    }
    Future.collect(fs)
  }
  private def destroyMarathonApp(appId: AppId)(implicit session: RequestSession): Future[MarathonAppDeleteSuccess] = {
    adminRouter.deleteApp(appId, force = true) map { resp =>
      resp.status match {
        case Status.Ok => MarathonAppDeleteSuccess()
        case _ => throw MarathonAppDeleteError(appId)
      }
    }
  }

  override def apply(req: rpc.v1.model.KillRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.KillResponse] = {
    // the following implementation is based on what the current CLI implementation does.
    // I've decided to follow it as close as possible so that we reduce any possible behavioral
    // changes that could have unforeseen consequences.
    //
    // In the future this will probably be revisited once Cosmos is the actual authority on services
    val f = req.appId match {
      case Some(appId) =>
        adminRouter.getAppOption(appId)
            .map {
              case Some(appResponse) =>
                createKillOperations(req.packageName, List(appResponse.app))
              case None =>
                throw KillNonExistentAppForPackage(req.packageName, appId)
            }
      case None =>
        adminRouter.listApps()
          .map { marathonApps =>
            createKillOperations(req.packageName, marathonApps.apps)
          }
    }

    f.map { killOperations =>
      req.all match {
        case Some(true) =>
          killOperations
        case _ if killOperations.size > 1 =>
          throw AmbiguousAppId(req.packageName, killOperations.map(_.appId))
        case _ => // we've only got one package running with the specified name, continue with it
          killOperations
      }
    }
      .flatMap(destroyMarathonAppsAndTearDownFrameworkIfPresent)
      .flatMap { killDetails =>
        Future.collect(
          killDetails.map { detail =>
            packageCache.getPackageByPackageVersion(detail.packageName, None)
              .map { case (pkg, _) =>
                detail -> pkg.postUninstallNotes
              }
          }
        )
      }
      .map { detailsAndNotes =>
        val results = detailsAndNotes.map { case (detail, postUninstallNotes) =>
          rpc.v1.model.KillResult(
            detail.packageName,
            detail.appId,
            detail.packageVersion,
            postUninstallNotes
          )
        }
        rpc.v1.model.KillResponse(results.toList)
      }
  }

  private[this] def createKillOperations(requestedPackageName: String, apps: List[MarathonApp]): List[KillOperation] = {
    val killOperations = for {
      app <- apps
      labels = app.labels
      packageName <- labels.get("DCOS_PACKAGE_NAME")
      if packageName == requestedPackageName
    } yield {
      KillOperation(
        appId = app.id,
        packageName = packageName,
        packageVersion = app.packageVersion.as[Option[universe.v2.model.PackageDetailsVersion]],
        frameworkName = labels.get("DCOS_PACKAGE_FRAMEWORK_NAME")
      )
    }

    if (killOperations.isEmpty) {
      throw PackageNotRunning(requestedPackageName)
    }
    killOperations
  }

  private case class MarathonAppDeleteSuccess()
  private case class KillOperation(
    appId: AppId,
    packageName: String,
    packageVersion: Option[universe.v2.model.PackageDetailsVersion],
    frameworkName: Option[String]
  )
  private case class KillDetails(
    appId: AppId,
    packageName: String,
    packageVersion: Option[universe.v2.model.PackageDetailsVersion],
    frameworkName: Option[String] = None,
    frameworkId: Option[String] = None
  )
  private case object KillDetails {
    def from(killOperation: KillOperation): KillDetails = {
      KillDetails(
        appId = killOperation.appId,
        packageName = killOperation.packageName,
        packageVersion = killOperation.packageVersion,
        frameworkName = killOperation.frameworkName
      )
    }
  }
}
