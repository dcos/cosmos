package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.internal.model.BundleDefinition
import com.mesosphere.universe.v3.model.Repository
import com.twitter.util.Future

trait PackageStorage {
  def getRepository: Future[Repository]
  def putPackageBundle(packageBundle: BundleDefinition): Future[Unit]
}
