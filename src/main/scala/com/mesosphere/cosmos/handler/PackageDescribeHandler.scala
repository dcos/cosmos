package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.PackageCache
import com.mesosphere.cosmos.http.{MediaTypes, MediaType}
import com.mesosphere.cosmos.model.{DescribeRequest, DescribeResponse}
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] class PackageDescribeHandler(packageCache: PackageCache)
  (implicit bodyDecoder: DecodeRequest[DescribeRequest], encoder: Encoder[DescribeResponse])
  extends EndpointHandler[DescribeRequest, DescribeResponse] {
  override def accepts: MediaType = MediaTypes.DescribeRequest
  override def produces: MediaType = MediaTypes.DescribeResponse

  override def apply(request: DescribeRequest): Future[DescribeResponse] = {
    packageCache
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .map { packageFiles =>
        DescribeResponse(
          packageFiles.packageJson,
          packageFiles.marathonJsonMustache,
          packageFiles.commandJson,
          packageFiles.configJson,
          packageFiles.resourceJson
        )
      }
  }
}
