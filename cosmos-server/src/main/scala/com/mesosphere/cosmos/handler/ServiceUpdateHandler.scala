package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.ServiceUpdater
import com.mesosphere.cosmos.error.AppIdChanged
import com.mesosphere.cosmos.error.OptionsNotStored
import com.mesosphere.cosmos.error.VersionUpgradeNotSupportedInOpen
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render.PackageDefinitionRenderer
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.ServiceUpdateRequest
import com.mesosphere.cosmos.service.CustomPackageManagerClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppResponse
import com.mesosphere.universe.common.JsonUtil
import com.netaporter.uri.Uri
import io.circe.JsonObject
import com.mesosphere.universe
import com.twitter.util.Future
import org.slf4j.Logger


final class ServiceUpdateHandler(
  adminRouter: AdminRouter,
  packageCollection: PackageCollection,
  serviceUpdater: ServiceUpdater
) extends EndpointHandler[rpc.v1.model.ServiceUpdateRequest, rpc.v1.model.ServiceUpdateResponse] {

  import ServiceUpdateHandler._

  override def apply(
    request: rpc.v1.model.ServiceUpdateRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.ServiceUpdateResponse] = {

    lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

    CustomPackageManagerClient.getCustomPackageManagerId(
      adminRouter,
      packageCollection,
      request.managerId,
      request.packageName,
      request.packageVersion,
      Option(request.appId)
    ).flatMap {
      case managerId if !managerId.isEmpty => {
        logger.info("Request requires a custom manager: " + managerId)
        CustomPackageManagerClient.callCustomServiceUpdate(
          adminRouter,
          request,
          managerId
        ).flatMap {
          case response =>
            Future {response}
        }
      }
      case managerId if managerId.isEmpty => {
        adminRouter.getApp(request.appId).flatMap { marathonAppResponse =>
          getPackageWithSourceOrThrow(packageCollection, marathonAppResponse.app).flatMap {
            case (packageDefinition, packageSource) =>
              if (request.packageVersion.exists(_ != packageDefinition.version)) {
                Future.exception(
                  VersionUpgradeNotSupportedInOpen(
                    request.packageVersion,
                    packageDefinition.version
                  ).exception
                )
              } else {
                update(
                  serviceUpdater,
                  marathonAppResponse,
                  request,
                  packageDefinition,
                  packageSource
                )
              }
          }
        }
      }
    }
  }

}

object ServiceUpdateHandler {

  def update(
    serviceUpdater: ServiceUpdater,
    marathonAppResponse: MarathonAppResponse,
    serviceUpdateRequest: ServiceUpdateRequest,
    packageDefinition: universe.v4.model.PackageDefinition,
    packageSource: Uri
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.ServiceUpdateResponse] = {
    val app = marathonAppResponse.app
    val storedOptions = app.serviceOptions
    val providedOptions = serviceUpdateRequest.options
    val options = mergeStoredAndProvided(storedOptions, providedOptions, serviceUpdateRequest.replace)
    val renderedMarathonJson = PackageDefinitionRenderer.renderMarathonV2App(
      sourceUri = packageSource,
      pkgDef = packageDefinition,
      options = options,
      marathonAppId = None
    ) match {
      case Some(renderedJson) =>
        for {id <- renderedJson("id").flatMap(_.asString)} {
          if (AppId(id) != serviceUpdateRequest.appId) {
            throw AppIdChanged(AppId(id), serviceUpdateRequest.appId).exception
          }
        }
        renderedJson
      case None =>
        throw new IllegalStateException("This service does not have a marathon template")
    }
    val resolvedOptions =
      PackageDefinitionRenderer.mergeDefaultAndUserOptions(packageDefinition, options)
    serviceUpdater.update(serviceUpdateRequest.appId, renderedMarathonJson).map { marathonDeploymentId =>
      rpc.v1.model.ServiceUpdateResponse(
        `package` = packageDefinition,
        resolvedOptions = resolvedOptions,
        marathonDeploymentId = marathonDeploymentId
      )
    }
  }

  def mergeStoredAndProvided(
    storedOptions: Option[JsonObject],
    providedOptions: Option[JsonObject],
    replace: Boolean
  ): Option[JsonObject] = {
    (replace, storedOptions, providedOptions) match {
      case (true, _, _) => providedOptions
      case (false, None, _) => throw OptionsNotStored().exception
      case (false, _: Some[_], None) => storedOptions
      case (false, Some(stored), Some(provided)) => Some(JsonUtil.merge(stored, provided))
    }
  }

}
