package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.internal.model.MarathonAppOps
import com.mesosphere.cosmos.internal.model.PackageOrigin
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.repository.CosmosRepository
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.thirdparty
import com.mesosphere.universe
import com.mesosphere.universe.v3.model.V2Package
import com.mesosphere.universe.v3.model.V3Package
import com.mesosphere.universe.v4.model.PackageDefinition
import com.mesosphere.universe.v4.model.V4Package
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import com.twitter.util.Try

private[cosmos] final class ListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Option[CosmosRepository]]
) extends EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse] {

  override def apply(
    request: rpc.v1.model.ListRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.ListResponse] = {
    for {
      apps <- getApplications(adminRouter, request)
    } yield {
      rpc.v1.model.ListResponse(apps.sortBy(install =>
        (install.packageInformation.packageDefinition.name, install.appId)))

    }
  }

  private[this] def getApplications(
    adminRouter: AdminRouter,
    request: rpc.v1.model.ListRequest
  )(
    implicit session: RequestSession
  ): Future[Seq[rpc.v1.model.Installation]] = {

    def satisfiesRequest(app: thirdparty.marathon.model.MarathonApp): Boolean = {
      // corner case: packageReleaseVersion will be None if parsing the label fails
      (app.packageReleaseVersion, app.packageName, app.packageRepository) match {
        case (Some(_), Some(pkgName), Some(_)) =>
          request.packageName.forall(_ == pkgName) && request.appId.forall(_ == app.id)
        case _ => false
      }
    }

    adminRouter.listApps().map { response =>
      response.apps.collect {
        case app if satisfiesRequest(app) =>
          rpc.v1.model.Installation(
            app.id,
            decodeInstalledPackageInformation(app)
          )
      }
    }
  }

  /* TODO: this may be helpful when we have PackageDefinition => Installation
  private[this] def setSelectedAndFramework(
    pkg: universe.v4.model.PackageDefinition
  ): universe.v4.model.PackageDefinition = {
    pkg match {
      case pkg: V3Package =>
        pkg.copy(
          selected = pkg.selected orElse Some(false),
          framework = pkg.framework orElse Some(false),
          resource = pkg.resource.map(_.copy(cli = None))
        )
      case pkg: V4Package =>
        pkg.copy(
          selected = pkg.selected orElse Some(false),
          framework = pkg.framework orElse Some(false),
          resource = pkg.resource.map(_.copy(cli = None))
        )
      case pkg: V2Package =>
        pkg.copy(
          selected = pkg.selected orElse Some(false),
          framework = pkg.framework orElse Some(false)
        )
    }
  }
  */

  private[this] def decodeInstalledPackageInformation(
    app: thirdparty.marathon.model.MarathonApp
  ): rpc.v1.model.InstalledPackageInformation = {
    // TODO: We nee to fix this in the next PR when we optionally persist this
    app.packageMetadata.as[rpc.v1.model.InstalledPackageInformation]
  }

}

