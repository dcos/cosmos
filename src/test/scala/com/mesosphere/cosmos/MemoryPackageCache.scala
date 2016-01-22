package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.PackageFiles
import com.twitter.util.Future

/** A package cache that stores all package information in memory. Useful for testing.
  *
  * @param packages the contents of the cache: package data indexed by package name
  */
final case class MemoryPackageCache(packages: Map[String, PackageFiles]) extends PackageCache {

  def getPackageFiles(
    packageName: String,
    version: Option[String]
  ): Future[PackageFiles] = {
    Future.value {
      packages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(a) => a
      }
    }
  }

}
