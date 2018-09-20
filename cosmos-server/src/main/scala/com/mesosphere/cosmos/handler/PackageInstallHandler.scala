package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.MarathonPackageRunner
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.PackageAlreadyInstalled
import com.mesosphere.cosmos.error.ServiceAlreadyStarted
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render.PackageDefinitionRenderer
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.repository.rewriteUrlWithProxyInfo
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.service.CustomPackageManagerRouter
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] final class PackageInstallHandler(
  packageCollection: PackageCollection,
  packageRunner: MarathonPackageRunner,
  customPackageManagerRouter: CustomPackageManagerRouter
) extends CustomEndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse] {

  private[this] lazy val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def apply(
    request: rpc.v1.model.InstallRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v2.model.InstallResponse] = {
    orElseGet(tryCustomPackageManager(request)) {
      packageCollection
        .getPackageByPackageVersion(
          request.packageName,
          request.packageVersion.as[Option[universe.v3.model.Version]]
        )
        .flatMap { case (pkg, sourceUri) =>
          PackageDefinitionRenderer.renderMarathonV2App(
            sourceUri,
            pkg,
            request.options,
            request.appId
          ) match {
            case Some(renderedMarathonJson) =>
              packageRunner.launch(renderedMarathonJson)
                .map { runnerResponse =>
                  rpc.v2.model.InstallResponse(
                    packageName = pkg.name,
                    packageVersion = pkg.version,
                    appId = Some(runnerResponse.id),
                    postInstallNotes = pkg.postInstallNotes,
                    cli = pkg.rewrite(rewriteUrlWithProxyInfo(session.originInfo), identity).cli
                  )
                }
                .handle {
                  case CosmosException(ServiceAlreadyStarted(_), _, _) =>
                    throw PackageAlreadyInstalled().exception
                }
            case None =>
              Future {
                rpc.v2.model.InstallResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = None,
                  postInstallNotes = pkg.postInstallNotes,
                  cli = pkg.rewrite(rewriteUrlWithProxyInfo(session.originInfo), identity).cli
                )
              }
          }
        }
    }
  }

  override def tryCustomPackageManager(
    request: rpc.v1.model.InstallRequest
  )(
    implicit session: RequestSession
  ): Future[Option[rpc.v2.model.InstallResponse]] = {
    customPackageManagerRouter.getCustomPackageManagerId(
      request.managerId,
      Option(request.packageName),
      request.packageVersion.as[Option[universe.v3.model.Version]],
      None
    ).flatMap {
      case Some((Some(managerId), _, _)) if !managerId.isEmpty =>
        logger.debug(s"Request [$request] requires a custom manager: [$managerId]")
        customPackageManagerRouter.callCustomPackageInstall(request, managerId).map(Some(_))
      case _ => Future(None)
    }
  }
}
