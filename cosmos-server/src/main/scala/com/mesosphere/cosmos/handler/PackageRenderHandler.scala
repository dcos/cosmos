package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error.ServiceMarathonTemplateNotFound
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] final class PackageRenderHandler(
  packageCollection: PackageCollection
) extends EndpointHandler[rpc.v1.model.RenderRequest, rpc.v1.model.RenderResponse] {

  override def apply(
    request: rpc.v1.model.RenderRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.RenderResponse] = {
    packageCollection
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.Version]]
      )
      .flatMap { case (pkg, uri) =>

        val packageConfig = PackageDefinitionRenderer.renderMarathonV2App(uri, pkg, request.options, request.appId)

        packageConfig match {
          case Some(renderedMarathonJson) =>
            Future.value(rpc.v1.model.RenderResponse(renderedMarathonJson))
          case None =>
            Future.exception(ServiceMarathonTemplateNotFound(pkg.name, pkg.version).exception)
        }
      }
  }
}
