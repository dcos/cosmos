package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.PackageCache
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] final class PackageRenderHandler(packageCache: PackageCache)
  (implicit bodyDecoder: DecodeRequest[RenderRequest], encoder: Encoder[RenderResponse])
  extends EndpointHandler[RenderRequest, RenderResponse] {

  val accepts = MediaTypes.RenderRequest
  val produces = MediaTypes.RenderResponse

  import PackageInstallHandler._

  override def apply(request: RenderRequest): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .map { packageFiles =>
        RenderResponse(
          preparePackageConfig(request.appId, request.options, packageFiles)
        )
      }
  }
}
