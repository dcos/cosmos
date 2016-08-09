package com.mesosphere.cosmos.storage

import java.util.concurrent.atomic.AtomicReference

import com.mesosphere.cosmos.RepositoryChangedDuringPublish
import com.mesosphere.cosmos.converter.Common.BundleToPackageDefinition
import com.mesosphere.universe.v3.model.{PackageBundle, PackageDefinition, Repository}
import com.twitter.bijection.Conversion.asMethod

final class InMemoryPackageStorage extends PackageStorage {
  private[this] val packages = new AtomicReference[Vector[PackageDefinition]](Vector())

  override def getRepository: Repository =
    Repository(
      packages.get.toList
    )

  override def putPackageBundle(packageBundle: PackageBundle): Unit = {
    val oldp = packages.get
    val newp = oldp :+ (packageBundle, PackageDefinition.ReleaseVersion(oldp.size).get).as[PackageDefinition]

    if(packages.compareAndSet(oldp, newp))
      throw RepositoryChangedDuringPublish()
  }
}
