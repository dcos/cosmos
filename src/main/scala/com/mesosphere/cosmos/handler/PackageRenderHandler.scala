package com.mesosphere.cosmos.handler

import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.Repository

private[cosmos] final class PackageRenderHandler(packageCache: Repository)
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
