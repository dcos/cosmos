package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageCollection
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
