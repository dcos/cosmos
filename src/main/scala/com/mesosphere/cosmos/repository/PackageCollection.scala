package com.mesosphere.cosmos.repository

import com.twitter.util.Future

import com.mesosphere.universe.PackageDetailsVersion
import com.mesosphere.universe.PackageFiles
import com.mesosphere.universe.UniverseIndexEntry

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
    packageVersion: Option[PackageDetailsVersion])
  : Future[PackageFiles]

  def getPackageIndex(packageName: String): Future[UniverseIndexEntry]

  def search(query: Option[String]): Future[List[UniverseIndexEntry]]
}
