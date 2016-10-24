package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.twitter.util.Future

// TODO (devflow) (jsancio): Make sure that we look at the operation store
final class DefaultLocalPackageCollection private (
  objectStorage: PackageObjectStorage
) extends LocalPackageCollection {
  override def list(): Future[List[rpc.v1.model.LocalPackage]] = {
    objectStorage.list().map(_.sorted.reverse)
  }
}

object DefaultLocalPackageCollection {
  def apply(objectStorage: PackageObjectStorage): DefaultLocalPackageCollection = {
    new DefaultLocalPackageCollection(objectStorage)
  }
}
