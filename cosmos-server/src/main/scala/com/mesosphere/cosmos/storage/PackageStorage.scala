package com.mesosphere.cosmos.storage

import com.mesosphere.universe.v3.model.{PackageBundle, Repository}
import com.twitter.util.Future

trait PackageStorage {
  def getRepository: Future[Repository]
  def putPackageBundle(packageBundle: PackageBundle): Future[Unit]
}
