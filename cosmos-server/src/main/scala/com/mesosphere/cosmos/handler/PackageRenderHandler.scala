package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.{PackageCollection, V3PackageCollection}
import com.mesosphere.cosmos.rpc.v1.model.{RenderRequest, RenderResponse}
import com.mesosphere.universe

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
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .map { packageFiles =>
        RenderResponse(
          preparePackageConfig(request.appId, request.options, packageFiles)
        )
      }
  }

}

// TODO (version): Rename to PackageRenderHandler
private[cosmos] final class V3PackageRenderHandler(
  packageCache: V3PackageCollection
) extends EndpointHandler[RenderRequest, RenderResponse] {

  import V3PackageInstallHandler._

  override def apply(request: RenderRequest)(implicit
    session: RequestSession
  ): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.PackageDefinition.Version]]
      )
      .map { case (v3Package, uri) =>
        RenderResponse(
          preparePackageConfig(request.appId, request.options, v3Package, uri)
        )
      }
  }

}
