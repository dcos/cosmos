package com.mesosphere.cosmos.storage

import com.mesosphere.universe.v3.model.{BundleDefinition, Repository}
import com.twitter.util.Future

trait PackageStorage {
  def getRepository: Future[Repository]
  def putPackageBundle(packageBundle: BundleDefinition): Future[Unit]
}
