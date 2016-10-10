package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

private[cosmos] final class PackageDescribeHandler(
  packageCollection: PackageCollection
) extends EndpointHandler[rpc.v1.model.DescribeRequest, universe.v3.model.PackageDefinition] {

  override def apply(request: rpc.v1.model.DescribeRequest)(implicit
    session: RequestSession
  ): Future[universe.v3.model.PackageDefinition] = {
    val packageInfo = packageCollection.getPackageByPackageVersion(
      request.packageName,
      request.packageVersion.as[Option[universe.v3.model.PackageDefinition.Version]]
    )

    packageInfo.map { case (pkg, _) => pkg }
  }

}
