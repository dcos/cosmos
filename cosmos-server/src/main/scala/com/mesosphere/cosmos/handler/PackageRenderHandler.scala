package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.ServiceMarathonTemplateNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc.v1.model.{RenderRequest, RenderResponse}
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] final class PackageRenderHandler(
  packageCache: PackageCollection
) extends EndpointHandler[RenderRequest, RenderResponse] {

  import PackageInstallHandler._

  override def apply(request: RenderRequest)(implicit
    session: RequestSession
  ): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.PackageDefinition.Version]]
      )
      .map { case (v3Package, uri) =>
        preparePackageConfig(request.appId, request.options, v3Package, uri) match {
          case Some(json) => RenderResponse(json)
          case None => throw ServiceMarathonTemplateNotFound(v3Package.name, v3Package.version)
        }
      }
  }

}
