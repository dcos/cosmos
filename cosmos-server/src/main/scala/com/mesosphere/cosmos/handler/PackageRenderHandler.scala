package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc.v1.model.RenderRequest
import com.mesosphere.cosmos.rpc.v1.model.RenderResponse
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] final class PackageRenderHandler(
  packageCache: PackageCollection
) extends EndpointHandler[RenderRequest, RenderResponse] {

  override def apply(
    request: RenderRequest
  )(
    implicit session: RequestSession
  ): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.Version]]
      )
      .flatMap { case (pkg, uri) =>

        val packageConfig = PackageDefinitionRenderer.renderMarathonV2App(uri, pkg, request.options, request.appId)

        packageConfig match {
          case Some(renderedMarathonJson) =>
            Future.value(RenderResponse(renderedMarathonJson))
          case None =>
            Future.exception(ServiceMarathonTemplateNotFound(pkg.name, pkg.version).exception)
        }
      }
  }
}
