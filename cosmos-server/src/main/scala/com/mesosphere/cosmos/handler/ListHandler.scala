package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.rewriteUrlWithProxyInfo
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.thirdparty
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] final class ListHandler(
  adminRouter: AdminRouter
) extends EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse] {

  override def apply(
    request: rpc.v1.model.ListRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.ListResponse] = {
    for {
      apps <- getApplications(adminRouter, request)
    } yield {
      rpc.v1.model.ListResponse(
        apps.sortBy(install => (install.packageInformation.packageDefinition.name, install.appId))
      )
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
      app.packageName.exists { pkgName =>
        request.packageName.forall(_ == pkgName) && request.appId.forall(_ == app.id)
      }
    }

    adminRouter.listApps().map { response =>
      response.apps.flatMap { app =>
        if (satisfiesRequest(app)) {
          decodeInstalledPackageInformation(app).map { info =>
            rpc.v1.model.Installation(
              app.id,
              info
            )
          }
        } else None
      }
    }
  }

  private[this] def decodeInstalledPackageInformation(
    app: thirdparty.marathon.model.MarathonApp
  )(
    implicit session: RequestSession
  ): Option[rpc.v1.model.InstalledPackageInformation] = {
    app.packageDefinition
      .map(
        _.rewrite(rewriteUrlWithProxyInfo(session.originInfo), identity)
         .as[rpc.v1.model.InstalledPackageInformation]
      )
  }

}

