package com.mesosphere.cosmos.repository

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

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v2.model.PackageDetailsVersion])
  : Future[universe.v2.model.PackageFiles]

  def getPackageIndex(packageName: String): Future[universe.v2.model.UniverseIndexEntry]

  def search(query: Option[String]): Future[List[rpc.v1.model.SearchResult]]

}

// TODO (version): Rename to PackageCollection
trait V3PackageCollection {

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): Future[(universe.v3.model.V3Package, Uri)]

  def search(query: Option[String]): Future[List[rpc.v1.model.SearchResult]]
}
