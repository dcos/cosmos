package com.mesosphere.cosmos.handler

import cats.instances.option._
import cats.syntax.apply._
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
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppResponse
import com.mesosphere.universe
import com.mesosphere.universe.common.JsonUtil
import com.netaporter.uri.Uri
import com.twitter.util.Future
import io.circe.JsonObject

final class ServiceUpdateHandler(
  adminRouter: AdminRouter,
  packageCollection: PackageCollection,
  serviceUpdater: ServiceUpdater
) extends EndpointHandler[rpc.v1.model.ServiceUpdateRequest, rpc.v1.model.ServiceUpdateResponse] {

  import ServiceUpdateHandler._

  type PackageWithSource = (universe.v4.model.PackageDefinition, Uri)

  override def apply(
    request: rpc.v1.model.ServiceUpdateRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.ServiceUpdateResponse] = {
    adminRouter.getApp(request.appId).flatMap { marathonAppResponse =>
      getPackageWithSource(marathonAppResponse.app).flatMap {
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
              marathonAppResponse,
              request,
              packageDefinition,
              packageSource
            )
          }
      }
    }
  }

  def update(
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

  private[this] def getPackageWithSource(
    marathonApp: MarathonApp
  )(
    implicit session: RequestSession
  ): Future[PackageWithSource] = {
    orElse(Future.value(getStoredPackageWithSource(marathonApp)))(
      lookupPackageWithSource(marathonApp)
    ).map(_.getOrElse {
      throw new IllegalStateException(
        "Unable to retrieve the old package definition"
      )
    })
  }

  private[this] def getStoredPackageWithSource(
    marathonApp: MarathonApp
  ): Option[PackageWithSource] = {
    (
      marathonApp.packageDefinition,
      marathonApp.packageRepository.map(_.uri)
    ).tupled
  }

  private[this] def lookupPackageWithSource(
    marathonApp: MarathonApp
  )(
    implicit session: RequestSession
  ): Future[Option[PackageWithSource]] = {
    traverse(getPackageCoordinate(marathonApp)) { case (name, version) =>
      packageCollection.getPackageByPackageVersion(name, Some(version))
    }
  }

  private[this] def getPackageCoordinate(
    marathonApp: MarathonApp
  ): Option[(String, universe.v3.model.Version)] = {
    val fromMetadata = marathonApp.packageMetadata.map { metadata =>
      val version = universe.v3.model.Version(metadata.version.toString)
      (metadata.name, version)
    }
    (
      marathonApp.packageName,
      marathonApp.packageVersion
    ).tupled
      .orElse(fromMetadata)
  }

}

object ServiceUpdateHandler {

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

  private def traverse[A, B](a: Option[A])(fun: A => Future[B]): Future[Option[B]] = {
    a.map(fun) match {
      case None => Future.value(None)
      case Some(v) => v.map(Some(_))
    }
  }

  private def orElse[A](
    f1: Future[Option[A]]
  )(
    f2: => Future[Option[A]]
  ): Future[Option[A]] = {
    f1.flatMap {
      case r: Some[A] => Future.value(r)
      case None => f2
    }
  }

}
