package com.mesosphere.cosmos

import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.model.{UninstallResult, UninstallRequest, UninstallResponse}
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import com.twitter.util.Future

private[cosmos] final class UninstallHandler(adminRouter: AdminRouter)
  extends Function[UninstallRequest, Future[CosmosResult[UninstallResponse]]] {

  private type FwIds = List[String]

  private def lookupFrameworkIds(fwName: String): Future[CosmosResult[FwIds]] = {
    adminRouter.getMasterState(fwName).map { xor =>
      xor.map { masterState =>
        masterState.frameworks
          .filter(_.name == fwName)
          .map(_.id)
      }
    }
  }
  private def destroyMarathonAppsAndTearDownFrameworkIfPresent(
    xor: CosmosResult[List[UninstallOperation]]
  ): Future[CosmosResult[List[UninstallDetails]]] = xor match {
    case Left(err) => Future.value(Left(err))
    case Right(uninstallOperations) =>
      val appDeletes = for {
        op <- uninstallOperations
        appId = op.appId
      } yield destroyMarathonApp(appId) flatMap {
        case Left(err) => Future.value(Left(err))
        case Right(_) =>
          op.frameworkName match {
            case Some(fwName) =>
              lookupFrameworkIds(fwName).flatMap {
                case Left(err) => Future.value(Left(err))
                case Right(fwIds) =>
                  fwIds match {
                    case Nil =>
                      Future.value(Right(UninstallDetails.from(op)))
                    case fwId :: Nil =>
                      adminRouter.tearDownFramework(fwId)
                        .map { xor =>
                          xor.map(_ => UninstallDetails.from(op).copy(frameworkId = Some(fwId)))
                        }
                    case all =>
                      Future.value(leftErrorNel(MultipleFrameworkIds(fwName, all)))
                  }
              }
            case None =>
              Future.value(Right(UninstallDetails.from(op)))
          }
      }

      Future.collect(appDeletes) map { xors =>
        import cats.std.list._
        import cats.syntax.traverse._
        xors.toList.sequenceU
      }
  }
  private def destroyMarathonApp(appId: String): Future[CosmosResult[MarathonAppDeleteSuccess]] = {
    adminRouter.deleteApp(appId, force = true) map { resp =>
      resp.status match {
        case Status.Ok => Right(MarathonAppDeleteSuccess())
        case a => leftErrorNel(MarathonAppDeleteError(appId))
      }
    }
  }

  override def apply(req: UninstallRequest): Future[CosmosResult[UninstallResponse]] = {
    // the following implementation is based on what the current CLI implementation does.
    // I've decided to follow it as close as possible so that we reduce any possible behavioral
    // changes that could have unforeseen consequences.
    //
    // In the future this will probably be revisited once Cosmos is the actual authority on services
    adminRouter.listApps().
      map {
        _.flatMap { marathonApps =>
          val appIds = for {
            app <- marathonApps.apps
            labels = app.labels
            packageName <- labels.get("DCOS_PACKAGE_NAME")
            if packageName == req.name
          } yield UninstallOperation(
            appId = app.id,
            packageName = packageName,
            version = labels.get("DCOS_PACKAGE_VERSION"),
            frameworkName = labels.get("DCOS_PACKAGE_FRAMEWORK_NAME")
          )

          val res: CosmosResult[List[UninstallOperation]] = req.all match {
            case Some(true) =>
              Right(appIds)
            case _ if appIds.size > 1 =>
              leftErrorNel(AmbiguousAppId(req.name, appIds.map(_.appId)))
            case _ => // we've only got one package installed with the name continue with it
              Right(appIds)
          }
          res
        }
      }
      .flatMap { xor =>
        destroyMarathonAppsAndTearDownFrameworkIfPresent(xor)
      }
      .map {
        _.map { uninstallDetails =>
          UninstallResponse(
            uninstallDetails.map { detail => UninstallResult(detail.packageName, detail.appId, detail.version) }
          )
        }
      }
  }

  private case class MarathonAppDeleteSuccess()
  private case class UninstallOperation(
    appId: String,
    packageName: String,
    version: Option[String],
    frameworkName: Option[String]
  )
  private case class UninstallDetails(
    appId: String,
    packageName: String,
    version: Option[String],
    frameworkName: Option[String] = None,
    frameworkId: Option[String] = None
  )
  private case object UninstallDetails {
    def from(uninstallOperation: UninstallOperation): UninstallDetails = {
      UninstallDetails(
        appId = uninstallOperation.appId,
        packageName = uninstallOperation.packageName,
        version = uninstallOperation.version,
        frameworkName = uninstallOperation.frameworkName
      )
    }
  }
}

