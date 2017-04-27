package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
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

  def getPackagesByPackageName(
    packageName: String
  )(implicit session: RequestSession): Future[List[universe.v4.model.PackageDefinition]]

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version]
  )(implicit session: RequestSession): Future[(universe.v4.model.PackageDefinition, Uri)]

  def search(
    query: Option[String]
  )(implicit session: RequestSession): Future[List[rpc.v1.model.SearchResult]]

  /**
   * Return the versions of packages in this collection that the package with the given `name` and
   * `version` can be upgraded to.
   */
  def upgradesTo(
    name: String,
    version: universe.v3.model.Version
  )(implicit session: RequestSession): Future[List[universe.v3.model.Version]]

}

object PackageCollection {

  /** Helper for the corresponding instance method */
  def upgradesTo(
    name: String,
    version: universe.v3.model.Version,
    possibleUpgrades: List[universe.v4.model.PackageDefinition]
  ): List[universe.v3.model.Version] = {
    possibleUpgrades.collect {
      case pkg if pkg.name == name && pkg.canUpgradeFrom(version) => pkg.version
    }
  }

}
