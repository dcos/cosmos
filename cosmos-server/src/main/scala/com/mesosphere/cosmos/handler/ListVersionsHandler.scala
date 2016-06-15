package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.{PackageCollection, V3PackageCollection}
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] class ListVersionsHandler(
  packageCache: PackageCollection
) extends EndpointHandler[rpc.v1.model.ListVersionsRequest, rpc.v1.model.ListVersionsResponse] {

  override def apply(request: rpc.v1.model.ListVersionsRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.ListVersionsResponse] = {
    packageCache
      .getPackageIndex(request.packageName)
      .map { packageInfo => rpc.v1.model.ListVersionsResponse(packageInfo.versions) }
  }

}

// TODO(version): Rename to ListVersionsHandler
final class V3ListVersionsHandler(
  packageCollection: V3PackageCollection
) extends EndpointHandler[rpc.v1.model.ListVersionsRequest, rpc.v1.model.ListVersionsResponse] {

  override def apply(request: rpc.v1.model.ListVersionsRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.ListVersionsResponse] = {
    packageCollection.getPackagesByPackageName(request.packageName)
      .map { packages =>
        val versions = packages.map { pkg =>
          val packageVersion = pkg.version.as[universe.v2.model.PackageDetailsVersion]
          val releaseVersion = pkg.releaseVersion.as[universe.v2.model.ReleaseVersion]

          packageVersion -> releaseVersion
        }

        rpc.v1.model.ListVersionsResponse(versions.toMap)
      }
  }
}
