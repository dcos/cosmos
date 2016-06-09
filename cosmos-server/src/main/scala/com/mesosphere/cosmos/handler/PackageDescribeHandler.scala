package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.{PackageCollection, V3PackageCollection}
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] class PackageDescribeHandler(
  packageCache: PackageCollection
) extends EndpointHandler[rpc.v1.model.DescribeRequest, rpc.v1.model.DescribeResponse] {

  override def apply(request: rpc.v1.model.DescribeRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.DescribeResponse] = {
    packageCache
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .map { packageFiles =>
        rpc.v1.model.DescribeResponse(
          packageFiles.packageJson,
          packageFiles.marathonJsonMustache,
          packageFiles.commandJson,
          packageFiles.configJson,
          packageFiles.resourceJson
        )
      }
  }

}

// TODO (version): Rename to PackageDescribeHandler
private[cosmos] final class V3PackageDescribeHandler(
  packageCollection: V3PackageCollection
) extends EndpointHandler[rpc.v1.model.DescribeRequest, rpc.v2.model.DescribeResponse] {

  override def apply(request: rpc.v1.model.DescribeRequest)(implicit
    session: RequestSession
  ): Future[universe.v3.model.V3Package] = {
    packageCollection.getPackageByPackageVersion(
      request.packageName,
      request.packageVersion.as[Option[universe.v3.model.PackageDefinition.Version]]
    )
  }

}
