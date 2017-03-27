package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future

/** An aggregation of packages, possibly from multiple repositories.
  *
  * Methods in this trait should be defined to work well even if they must interact with remote
  * services. For example, they should perform filtering on their data before returning it. That
  * way, a hierarchy of [[PackageCollection]]s can work together without generating unwieldy amounts
  * of data.
  */
trait PackageCollection {

  def getPackagesByPackageName(packageName: String)(implicit session: RequestSession): Future[List[universe.v3.model.PackageDefinition]]

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version]
  )(implicit session: RequestSession): Future[(universe.v3.model.PackageDefinition, Uri)]

  def search(query: Option[String])(implicit session: RequestSession): Future[List[rpc.v1.model.SearchResult]]
}
