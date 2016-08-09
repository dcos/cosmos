package com.mesosphere.cosmos.storage

import com.mesosphere.universe.v3.model.{PackageBundle, Repository}

trait PackageStorage {
  def getRepository: Repository
  def putPackageBundle(packageBundle: PackageBundle): Unit
}
