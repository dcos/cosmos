package com.mesosphere.cosmos

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import com.mesosphere.packagestore.v3.model.PackageBundle
import com.mesosphere.universe.v3.model.{PackageDefinition, Repository}

case class Storage() {
  private[this] val nextReleaseVersion: AtomicInteger = new AtomicInteger(0)
  private[this] val packages: ConcurrentLinkedQueue[PackageDefinition] = new ConcurrentLinkedQueue

  def getRepository: Repository = {
    val arrayType = Array[PackageDefinition]()
    Repository(
      packages.toArray(arrayType).toList
    )
  }

  def putPackageBundle(packageBundle: PackageBundle): PackageDefinition = {
    val releaseVersion = PackageDefinition.ReleaseVersion(
        nextReleaseVersion.incrementAndGet()
    ).get
    val pkg = packageBundle.toPackageDefinition(releaseVersion)
    packages.add(pkg)
    pkg
  }

}
